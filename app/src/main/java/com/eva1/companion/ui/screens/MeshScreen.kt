package com.eva1.companion.ui.screens

import androidx.compose.runtime.Composable
import com.eva1.companion.EvaIntent
import com.eva1.companion.EvaUiState
import com.eva1.companion.mesh.MeshNetworkScreen
import com.eva1.companion.mesh.SwarmMeshManager

@Composable
fun MeshScreen(
    state: EvaUiState,
    swarmManager: SwarmMeshManager,
    onIntent: (EvaIntent) -> Unit
) {
    MeshNetworkScreen(
        swarmManager = swarmManager,
        blePeers = state.meshPeers,
        bleMessages = state.meshMessages,
        onSendBleMessage = { msg -> onIntent(EvaIntent.SendMeshMessage(msg)) }
    )
}
