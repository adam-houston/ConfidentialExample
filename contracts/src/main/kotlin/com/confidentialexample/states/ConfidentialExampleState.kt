package com.confidentialexample.states

import com.confidentialexample.contracts.ConfidentialExampleContract
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import java.util.*

// *********
// * State *
// *********
@BelongsToContract(ConfidentialExampleContract::class)
data class ConfidentialExampleState(val data: String,
                                    val owner: AbstractParty,
                                    val otherParticipants: List<AbstractParty> = listOf(),
                                    override val linearId: UniqueIdentifier = UniqueIdentifier()) : LinearState {

    override val participants: List<AbstractParty>
        get() = (otherParticipants + owner)

    fun withNewData(newData: String) : ConfidentialExampleState {
        return this.copy( data = newData )
    }
}
