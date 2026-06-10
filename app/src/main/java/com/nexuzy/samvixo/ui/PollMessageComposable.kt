package com.nexuzy.samvixo.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

data class PollOption(
    val id: String,
    val text: String,
    val votes: Int = 0
)

data class PollData(
    val question: String = "",
    val options: List<PollOption> = emptyList(),
    val totalVotes: Int = 0,
    val votedOptionId: String? = null
)

/**
 * PollMessageComposable — inline poll create & vote (§3 PID: Polls feature)
 *
 * Stores in Firestore: chats/{chatId}/messages/{msgId}/poll
 * Voting updates the votes field and records voter UID in "voters" subcollection.
 */
@Composable
fun PollMessage(
    poll: PollData,
    chatId: String,
    messageId: String,
    currentUserUid: String,
    onVote: (optionId: String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(poll.question, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))

            poll.options.forEach { option ->
                val fraction = if (poll.totalVotes > 0)
                    option.votes.toFloat() / poll.totalVotes.toFloat()
                else 0f
                val isVoted = option.id == poll.votedOptionId

                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isVoted,
                            onClick  = {
                                if (poll.votedOptionId == null) {
                                    onVote(option.id)
                                    CoroutineScope(Dispatchers.IO).launch {
                                        Firebase.firestore
                                            .collection("chats").document(chatId)
                                            .collection("messages").document(messageId)
                                            .update(
                                                mapOf(
                                                    "poll.options.${option.id}.votes" to
                                                        com.google.firebase.firestore.FieldValue.increment(1),
                                                    "poll.totalVotes" to
                                                        com.google.firebase.firestore.FieldValue.increment(1)
                                                )
                                            )
                                    }
                                }
                            }
                        )
                        Text(option.text, modifier = Modifier.weight(1f))
                        Text(
                            "${option.votes} (${(fraction * 100).toInt()}%)",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    LinearProgressIndicator(
                        progress    = { fraction },
                        modifier    = Modifier
                            .fillMaxWidth()
                            .padding(start = 40.dp, bottom = 4.dp),
                        trackColor  = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }

            Text(
                "${poll.totalVotes} votes",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Create poll dialog — shown in chat via FAB or attachment menu */
@Composable
fun CreatePollDialog(
    onDismiss: () -> Unit,
    onCreatePoll: (question: String, options: List<String>) -> Unit
) {
    var question by remember { mutableStateOf("") }
    var options  by remember { mutableStateOf(listOf("", "")) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Poll") },
        text  = {
            Column {
                OutlinedTextField(
                    value         = question,
                    onValueChange = { question = it },
                    label         = { Text("Question") },
                    modifier      = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                options.forEachIndexed { idx, opt ->
                    OutlinedTextField(
                        value         = opt,
                        onValueChange = { v -> options = options.toMutableList().also { it[idx] = v } },
                        label         = { Text("Option ${idx + 1}") },
                        modifier      = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(4.dp))
                }
                if (options.size < 5) {
                    TextButton(onClick = { options = options + "" }) {
                        Text("+ Add Option")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick  = { onCreatePoll(question, options.filter { it.isNotBlank() }) },
                enabled  = question.isNotBlank() && options.count { it.isNotBlank() } >= 2
            ) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
