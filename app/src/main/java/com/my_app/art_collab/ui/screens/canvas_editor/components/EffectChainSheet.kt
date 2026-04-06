package com.my_app.art_collab.ui.screens.canvas_editor.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.my_app.art_collab.domain.model.Effect
import com.my_app.art_collab.domain.model.Layer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EffectChainSheet (
    layer: Layer,
    effectChain: List<Effect>,
    onAddEffect: (Effect)->Unit,
    onUpdateEffect: (Effect)->Unit,
    onRemoveEffect: (effectId: String)->Unit,
    onToggleEffect: (effectId: String)->Unit,
    onDismiss: ()->Unit
){
    var showEffectPicker by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding()
        ){
            Row(modifier = Modifier.fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text(text="Effects - ${layer.name}"
                ,style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = {showEffectPicker=true}) {
                    Text("Add Effect")
                }
            }
            if(effectChain.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No effects added yet.\nTap \"Add Effect\" to get started.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }else{
                    LazyColumn(modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)) {
                        items(effectChain,key={it.id}){
                            effect->
                            EffectItem(
                                effect = effect,
                                onUpdate = onUpdateEffect,
                                onRemove = {onRemoveEffect(effect.id)},
                                onToggleEnabled = {onToggleEffect(effect.id)}
                            )
                    }
                }
            }
            Spacer(modifier= Modifier.padding(bottom = 16.dp))
        }
    }
    if(showEffectPicker) {
        EffectPickerDialog(
            onDismiss = { showEffectPicker = false },
            onEffectSelected = { effect ->
                onAddEffect(effect)
                showEffectPicker = false
            }
        )
    }
}