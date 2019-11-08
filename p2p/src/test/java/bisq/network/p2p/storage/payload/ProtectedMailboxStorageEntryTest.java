/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.network.p2p.storage.payload;

import bisq.network.p2p.PrefixedSealedAndSignedMessage;
import bisq.network.p2p.TestUtils;
import bisq.network.p2p.storage.P2PDataStorage;

import bisq.common.app.Version;
import bisq.common.crypto.CryptoException;
import bisq.common.crypto.Sig;

import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;

import java.time.Clock;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import static org.mockito.Mockito.*;

public class ProtectedMailboxStorageEntryTest {

    private static MailboxStoragePayload buildMailboxStoragePayload(PublicKey payloadSenderPubKeyForAddOperation,
                                                                    PublicKey payloadOwnerPubKey) {

        // Mock out the PrefixedSealedAndSignedMessage with a version that just serializes to the DEFAULT_INSTANCE
        // in protobuf. This object is never validated in the test, but needs to be hashed as part of the testing path.
        PrefixedSealedAndSignedMessage prefixedSealedAndSignedMessageMock = mock(PrefixedSealedAndSignedMessage.class);
        protobuf.NetworkEnvelope networkEnvelopeMock = mock(protobuf.NetworkEnvelope.class);
        when(networkEnvelopeMock.getPrefixedSealedAndSignedMessage()).thenReturn(
                protobuf.PrefixedSealedAndSignedMessage.getDefaultInstance());
        when(prefixedSealedAndSignedMessageMock.toProtoNetworkEnvelope()).thenReturn(networkEnvelopeMock);

        return new MailboxStoragePayload(
                prefixedSealedAndSignedMessageMock, payloadSenderPubKeyForAddOperation, payloadOwnerPubKey);
    }

    private static ProtectedMailboxStorageEntry buildProtectedMailboxStorageEntry(MailboxStoragePayload mailboxStoragePayload, KeyPair ownerKey, PublicKey receiverKey) throws CryptoException {
        int sequenceNumber = 1;

        byte[] hashOfDataAndSeqNr = P2PDataStorage.get32ByteHash(new P2PDataStorage.DataAndSeqNrPair(mailboxStoragePayload, sequenceNumber));
        byte[] signature = Sig.sign(ownerKey.getPrivate(), hashOfDataAndSeqNr);

        return new ProtectedMailboxStorageEntry(mailboxStoragePayload, ownerKey.getPublic(), sequenceNumber, signature, receiverKey, Clock.systemDefaultZone());
    }

    @Before
    public void SetUp() {
        // Deep in the bowels of protobuf we grab the messageID from the version module. This is required to hash the
        // full MailboxStoragePayload so make sure it is initialized.
        Version.setBaseCryptoNetworkId(1);
    }

    // TESTCASE: validForAddOperation() should return true if the Entry owner and sender key specified in payload match
    @Test
    public void isValidForAddOperation() throws NoSuchAlgorithmException, CryptoException {
        KeyPair senderKeys = TestUtils.generateKeyPair();
        KeyPair receiverKeys = TestUtils.generateKeyPair();

        MailboxStoragePayload mailboxStoragePayload = buildMailboxStoragePayload(senderKeys.getPublic(), receiverKeys.getPublic());
        ProtectedStorageEntry protectedStorageEntry = buildProtectedMailboxStorageEntry(mailboxStoragePayload, senderKeys, receiverKeys.getPublic());

        Assert.assertTrue(protectedStorageEntry.isValidForAddOperation());
    }

    // TESTCASE: validForAddOperation() should return false if the Entry owner and sender key specified in payload don't match
    @Test
    public void isValidForAddOperation_EntryOwnerPayloadReceiverMismatch() throws NoSuchAlgorithmException, CryptoException {
        KeyPair senderKeys = TestUtils.generateKeyPair();
        KeyPair receiverKeys = TestUtils.generateKeyPair();

        MailboxStoragePayload mailboxStoragePayload = buildMailboxStoragePayload(senderKeys.getPublic(), receiverKeys.getPublic());
        ProtectedStorageEntry protectedStorageEntry = buildProtectedMailboxStorageEntry(mailboxStoragePayload, receiverKeys, receiverKeys.getPublic());

        Assert.assertFalse(protectedStorageEntry.isValidForAddOperation());
    }

    // TESTCASE: validForAddOperation() should fail if Entry.receiversPubKey and Payload.ownerPubKey don't match
    // XXXBUGXXX: The current code doesn't validate this mismatch, but it would create an added payload that could never
    // be removed since the remove code requires Entry.receiversPubKey == Payload.ownerPubKey
    @Test
    public void isValidForAddOperation_EntryReceiverPayloadReceiverMismatch() throws NoSuchAlgorithmException, CryptoException {
        KeyPair senderKeys = TestUtils.generateKeyPair();
        KeyPair receiverKeys = TestUtils.generateKeyPair();

        MailboxStoragePayload mailboxStoragePayload = buildMailboxStoragePayload(senderKeys.getPublic(), receiverKeys.getPublic());
        ProtectedStorageEntry protectedStorageEntry = buildProtectedMailboxStorageEntry(mailboxStoragePayload, senderKeys, senderKeys.getPublic());

        // should be assertFalse
        Assert.assertTrue(protectedStorageEntry.isValidForAddOperation());
    }

    // TESTCASE: validForAddOperation() should fail if the signature isn't valid
    @Test
    public void isValidForAddOperation_BadSignature() throws NoSuchAlgorithmException, CryptoException {
        KeyPair senderKeys = TestUtils.generateKeyPair();
        KeyPair receiverKeys = TestUtils.generateKeyPair();

        MailboxStoragePayload mailboxStoragePayload = buildMailboxStoragePayload(senderKeys.getPublic(), receiverKeys.getPublic());
        ProtectedStorageEntry protectedStorageEntry = buildProtectedMailboxStorageEntry(mailboxStoragePayload, senderKeys, receiverKeys.getPublic());

        protectedStorageEntry.updateSignature( new byte[] { 0 });

        Assert.assertFalse(protectedStorageEntry.isValidForAddOperation());
    }

    // TESTCASE: validForRemoveOperation() should return true if the Entry owner and payload owner match
    @Test
    public void validForRemove() throws NoSuchAlgorithmException, CryptoException {
        KeyPair senderKeys = TestUtils.generateKeyPair();
        KeyPair receiverKeys = TestUtils.generateKeyPair();

        MailboxStoragePayload mailboxStoragePayload = buildMailboxStoragePayload(senderKeys.getPublic(), receiverKeys.getPublic());
        ProtectedStorageEntry protectedStorageEntry = buildProtectedMailboxStorageEntry(mailboxStoragePayload, receiverKeys, receiverKeys.getPublic());

        Assert.assertTrue(protectedStorageEntry.isValidForRemoveOperation());
    }

    // TESTCASE: validForRemoveOperation() should return false if the Entry owner and payload owner don't match
    @Test
    public void validForRemoveEntryOwnerPayloadOwnerMismatch() throws NoSuchAlgorithmException, CryptoException {
        KeyPair senderKeys = TestUtils.generateKeyPair();
        KeyPair receiverKeys = TestUtils.generateKeyPair();

        MailboxStoragePayload mailboxStoragePayload = buildMailboxStoragePayload(senderKeys.getPublic(), receiverKeys.getPublic());
        ProtectedStorageEntry protectedStorageEntry = buildProtectedMailboxStorageEntry(mailboxStoragePayload, senderKeys, receiverKeys.getPublic());

        Assert.assertFalse(protectedStorageEntry.isValidForRemoveOperation());
    }

    // TESTCASE: isValidForRemoveOperation() should fail if the signature is bad
    @Test
    public void isValidForRemoveOperation_BadSignature() throws NoSuchAlgorithmException, CryptoException {
        KeyPair senderKeys = TestUtils.generateKeyPair();
        KeyPair receiverKeys = TestUtils.generateKeyPair();

        MailboxStoragePayload mailboxStoragePayload = buildMailboxStoragePayload(senderKeys.getPublic(), receiverKeys.getPublic());
        ProtectedStorageEntry protectedStorageEntry = buildProtectedMailboxStorageEntry(mailboxStoragePayload, receiverKeys, receiverKeys.getPublic());

        protectedStorageEntry.updateSignature(new byte[] { 0 });

        Assert.assertFalse(protectedStorageEntry.isValidForRemoveOperation());
    }

    // TESTCASE: isValidForRemoveOperation() should fail if the receiversPubKey does not match the Entry owner
    @Test
    public void isValidForRemoveOperation_ReceiversPubKeyMismatch() throws NoSuchAlgorithmException, CryptoException {
        KeyPair senderKeys = TestUtils.generateKeyPair();
        KeyPair receiverKeys = TestUtils.generateKeyPair();

        MailboxStoragePayload mailboxStoragePayload = buildMailboxStoragePayload(senderKeys.getPublic(), receiverKeys.getPublic());
        ProtectedStorageEntry protectedStorageEntry = buildProtectedMailboxStorageEntry(mailboxStoragePayload, receiverKeys, senderKeys.getPublic());

        Assert.assertFalse(protectedStorageEntry.isValidForRemoveOperation());
    }
}
