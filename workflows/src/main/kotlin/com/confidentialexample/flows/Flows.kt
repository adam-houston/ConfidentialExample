package com.confidentialexample.flows

import co.paralleluniverse.fibers.Suspendable
import com.confidentialexample.contracts.ConfidentialExampleContract
import com.confidentialexample.states.ConfidentialExampleState
import net.corda.core.contracts.Command
import net.corda.core.contracts.StateAndContract
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import com.r3.corda.lib.ci.workflows.RequestKey
import com.r3.corda.lib.ci.workflows.SyncKeyMappingInitiator
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.contracts.requireThat
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction

// *********
// * Flows *
// *********

/**
 * Create a new state in the vault with ourIdentity as the (confidential) owner, and two other confidential parties.
 *
 * Only our identity will be shared with the other participants. They will not know who each other is.
 */
@InitiatingFlow
@StartableByRPC
class Create(private val theData :String,
                private val party1 : Party,
                private val party2 : Party) : FlowLogic<Unit>() {
    override val progressTracker = ProgressTracker()

    @Suspendable
    override fun call() {
        val txb = TransactionBuilder(notary = serviceHub.networkMapCache.notaryIdentities.first())
        val cmd = ConfidentialExampleContract.Commands.Create()

        // get anonymous identities for all parties
        val anonParty1 = subFlow(RequestKey(party1))
        val anonParty2 = subFlow(RequestKey(party2))
        val anonSelf = subFlow(RequestKey(ourIdentity))

        val state = ConfidentialExampleState(theData, anonSelf, listOf(anonParty2, anonParty1))
        txb.withItems(
                StateAndContract(state, ConfidentialExampleContract.ID),
                Command(cmd, listOf(party1, party2, ourIdentity).map { it.owningKey })
        )

        txb.verify(serviceHub)

        // share only our identity with other parties - do not share their identities with each other
        subFlow(SyncKeyMappingInitiator(party1, listOf(anonSelf)))
        subFlow(SyncKeyMappingInitiator(party2, listOf(anonSelf)))

        val partialTx = serviceHub.signInitialTransaction(txb)

        val sessions = listOf(party1, party2).map { initiateFlow(it) }

        val signedTx = subFlow(CollectSignaturesFlow(partialTx, sessions))

        subFlow(FinalityFlow(signedTx, sessions))
    }
}

@InitiatedBy(Create::class)
class Responder(val counterpartySession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val signedTransactionFlow = object : SignTransactionFlow(counterpartySession) {
            override fun checkTransaction(stx: SignedTransaction) = requireThat {

            }
        }
        val expectedTxId = subFlow(signedTransactionFlow).id

        subFlow(ReceiveFinalityFlow(counterpartySession, expectedTxId))
    }
}

/**
 * Update the state with a new string value
 *
 * This can only succeed if the caller knows the well known identity of the other participants. After creation, only
 * the owner knows the identity of the other participants until such time as the owner shares the other keys by
 * invoking the ShareKeys flow.
 */
@InitiatingFlow
@StartableByRPC
class Update(private val linearId : UniqueIdentifier, private val theData : String) : FlowLogic<Unit>() {
    override val progressTracker = ProgressTracker()

    @Suspendable
    override fun call() {
        val txb = TransactionBuilder(notary = serviceHub.networkMapCache.notaryIdentities.first())
        val cmd = ConfidentialExampleContract.Commands.Update()

        val criteria = QueryCriteria.LinearStateQueryCriteria(linearId = listOf(linearId))

        val oldStateAndRef = serviceHub.vaultService.queryBy(ConfidentialExampleState::class.java, criteria).states.first()

        // Get a list of well known parties. This will throw if cannot resolve any of the parties
        val parties = oldStateAndRef.state.data.participants.map {
            serviceHub.identityService.requireWellKnownPartyFromAnonymous(it)
        }

        val state = oldStateAndRef.state.data.withNewData(theData)
        txb.withItems(
                oldStateAndRef,
                StateAndContract(state, ConfidentialExampleContract.ID),
                Command(cmd, parties.map { it.owningKey })
        )

        txb.verify(serviceHub)

        val partialTx = serviceHub.signInitialTransaction(txb)

        val sessions = (parties - ourIdentity).map { initiateFlow(it) }

        val signedTx = subFlow(CollectSignaturesFlow(partialTx, sessions))

        subFlow(FinalityFlow(signedTx, sessions))
    }
}

@InitiatedBy(Update::class)
class UpdateResponder(val counterpartySession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val signedTransactionFlow = object : SignTransactionFlow(counterpartySession) {
            override fun checkTransaction(stx: SignedTransaction) = requireThat {

            }
        }
        val expectedTxId = subFlow(signedTransactionFlow).id

        subFlow(ReceiveFinalityFlow(counterpartySession, expectedTxId))
    }
}

/**
 * Share the confidential identities with all participants
 */
@InitiatingFlow
@StartableByRPC
class ShareKeys(private val linearId : UniqueIdentifier) : FlowLogic<Unit>() {
    override val progressTracker = ProgressTracker()

    @Suspendable
    override fun call() {

        // Lookup the state that we wish to share keys for
        val criteria = QueryCriteria.LinearStateQueryCriteria(linearId = listOf(linearId))
        val theState = serviceHub.vaultService.queryBy(
                ConfidentialExampleState::class.java, criteria
        ).states.first().state

        // Get a map of well known parties -> anonymous parties for the other participants
        var wellKnownToAnonymousOthers = theState.data.otherParticipants.map {

            // requireWellKnownPartyFromAnonymous will throw exception if cannot resolve.
            // Use wellKnownPartyFromAnonymous if you need to get a 'null' if if the well known party cannot be found
            serviceHub.identityService.requireWellKnownPartyFromAnonymous(it) to it
        }.toMap()

        // share well known -> anonymous keys
        wellKnownToAnonymousOthers.forEach {

            // The party we are about to share with
            val partyToShareWith = it.key

            // we don't need to include the party's own keys (it already knows about itself), so remove party from map
            val otherPartiesWellKnownToAnon = (wellKnownToAnonymousOthers - partyToShareWith)

            // get all the anon keys from the map ready to share them
            val keysToShare = otherPartiesWellKnownToAnon.map { wellKnownToAnonPair -> wellKnownToAnonPair.value }

            // now do the sharing
            subFlow(SyncKeyMappingInitiator(partyToShareWith, keysToShare))
        }
    }
}
