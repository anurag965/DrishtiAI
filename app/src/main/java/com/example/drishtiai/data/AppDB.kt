package com.example.drishtiai.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import org.koin.core.annotation.Single
import java.util.Date

@Database(
    entities = [Chat::class, ChatMessage::class, LLMModel::class, Task::class, Folder::class],
    version = 1,
)
@TypeConverters(Converters::class)
abstract class AppRoomDatabase : RoomDatabase() {
    abstract fun chatsDao(): ChatsDao

    abstract fun chatMessagesDao(): ChatMessageDao

    abstract fun llmModelDao(): LLMModelDao

    abstract fun taskDao(): TaskDao

    abstract fun folderDao(): FolderDao
}

@Single
class AppDB(context: Context) {
    private val db =
        Room.databaseBuilder(context, AppRoomDatabase::class.java, "app-database").fallbackToDestructiveMigration().build()

    /** Get all chats from the database sorted by dateUsed in descending order. */
    fun getChats(): Flow<List<Chat>> = db.chatsDao().getChats()

    fun loadDefaultChat(): Chat {
        val defaultChat =
            if (getChatsCount() == 0L) {
                addChat("Untitled")
                getRecentlyUsedChat()!!
            } else {
                // Given that chatsDB has at least one chat
                // chatsDB.getRecentlyUsedChat() will never return null
                getRecentlyUsedChat()!!
            }
        return defaultChat
    }

    /**
     * Get the most recently used chat from the database. This function might return null, if there
     * are no chats in the database.
     */
    fun getRecentlyUsedChat(): Chat? =
        runBlocking(Dispatchers.IO) { db.chatsDao().getRecentlyUsedChat() }

    /**
     * Adds a new chat to the database initialized with given arguments and returns the new Chat
     * object
     */
    fun addChat(
        chatName: String,
        chatTemplate: String = "",
        systemPrompt: String = """You are responsible for generating audio descriptions for video content to ensure accessibility for blind and low vision individuals. Descriptions must be informative, objective, and conversational, delivered in the present tense and from a third-person omniscient perspective unless first-person narration is explicitly required for audience engagement. Begin each description with general context and then add only the details necessary for understanding the scene, prioritizing relevance to the plot, learning outcomes, or viewer enjoyment. Avoid over-description, personal interpretation, opinionated language, or unnecessary visual detail. Describe only what a sighted viewer can see, and never alter, filter, censor, or exclude content.

Vocabulary must align with the program’s genre, tone, historical context, and intended age group, using formal speech unless the content clearly requires otherwise. Word choice should be accurate, neutral, and free of bias or negative connotations. Use vivid, precise verbs rather than adverbs, and apply articles correctly when introducing or referring to subjects. Maintain consistency in terminology, character traits, and visual elements throughout the description. Pronouns should only be used when their reference is unmistakably clear. When comparisons are helpful, relate shapes and sizes to familiar, globally recognizable objects.

Describe characters factually and consistently, focusing on visible traits relevant to identity and personality such as hair, skin, eyes, build, approximate age, height, clothing, and visible disabilities, using person-first language. Do not guess or assume race, ethnicity, gender identity, or other attributes unless they are visually evident and established within the content. When naming a character for the first time, include a brief visual descriptor before the name. Convey facial expressions, body language, and reactions when they contribute to meaning or emotional understanding. Clothing should only be described when it enhances characterization, setting, plot, or genre appreciation.

When relevant, include location, time of day, weather conditions, and setting. For dramatic productions, incorporate essential elements such as period, style, dress, aesthetics, key objects, and shifts in focus. When shot changes are critical to comprehension, indicate the transition by describing the new location or where characters appear in the frame. For instructional content, describe the sequence of activities clearly and logically before detailing individual actions.

On-screen text must be read when it is central to understanding the content. Establish a consistent pattern when reading on-screen words, such as announcing that words appear. For subtitles, state that a subtitle appears before reading the translation. Maintain steady, impartial narration that matches the program’s tone while allowing for appropriate emotional modulation when the genre calls for excitement, tension, or lightness.

Your output must be formatted strictly as a dictionary with two keys: "Video_Category", a string indicating the category of the video, and "Revised_Desc", a single cohesive description string that adheres to all the above principles. Avoid any prefatory language such as “the video shows” or similar constructions.""",
        llmModelId: Long = -1,
        isTask: Boolean = false,
    ): Chat =
        runBlocking(Dispatchers.IO) {
            val newChat =
                Chat(
                    name = chatName,
                    systemPrompt = systemPrompt,
                    dateCreated = Date(),
                    dateUsed = Date(),
                    llmModelId = llmModelId,
                    contextSize = 2048,
                    chatTemplate = chatTemplate,
                    isTask = isTask,
                )
            val newChatId = db.chatsDao().insertChat(newChat)
            newChat.copy(id = newChatId)
        }

    /** Update the chat in the database. */
    fun updateChat(modifiedChat: Chat) =
        runBlocking(Dispatchers.IO) { db.chatsDao().updateChat(modifiedChat) }

    fun deleteChat(chat: Chat) = runBlocking(Dispatchers.IO) { db.chatsDao().deleteChat(chat.id) }

    fun getChatsCount(): Long = runBlocking(Dispatchers.IO) { db.chatsDao().getChatsCount() }

    fun getChatsForFolder(folderId: Long): Flow<List<Chat>> =
        db.chatsDao().getChatsForFolder(folderId)

    // Chat Messages

    fun getMessages(chatId: Long): Flow<List<ChatMessage>> =
        db.chatMessagesDao().getMessages(chatId)

    fun getMessagesForModel(chatId: Long): List<ChatMessage> =
        runBlocking(Dispatchers.IO) { db.chatMessagesDao().getMessagesForModel(chatId) }

    fun addUserMessage(chatId: Long, message: String) =
        runBlocking(Dispatchers.IO) {
            db.chatMessagesDao()
                .insertMessage(
                    ChatMessage(chatId = chatId, message = message, isUserMessage = true)
                )
        }

    fun addAssistantMessage(chatId: Long, message: String) =
        runBlocking(Dispatchers.IO) {
            db.chatMessagesDao()
                .insertMessage(
                    ChatMessage(chatId = chatId, message = message, isUserMessage = false)
                )
        }

    fun deleteMessage(messageId: Long) =
        runBlocking(Dispatchers.IO) { db.chatMessagesDao().deleteMessage(messageId) }

    fun deleteMessages(chatId: Long) =
        runBlocking(Dispatchers.IO) { db.chatMessagesDao().deleteMessages(chatId) }

    // Models

    fun addModel(name: String, url: String, path: String, mmProjPath: String, contextSize: Int, chatTemplate: String) =
        runBlocking(Dispatchers.IO) {
            db.llmModelDao()
                .insertModels(
                    LLMModel(
                        name = name,
                        url = url,
                        path = path,
                        mmProjPath = mmProjPath,
                        contextSize = contextSize,
                        chatTemplate = chatTemplate,
                    )
                )
        }

    fun getModel(id: Long): LLMModel = runBlocking(Dispatchers.IO) { db.llmModelDao().getModel(id) }

    fun getModels(): Flow<List<LLMModel>> =
        runBlocking(Dispatchers.IO) { db.llmModelDao().getAllModels() }

    fun getModelsList(): List<LLMModel> =
        runBlocking(Dispatchers.IO) { db.llmModelDao().getAllModelsList() }

    fun deleteModel(id: Long) = runBlocking(Dispatchers.IO) { db.llmModelDao().deleteModel(id) }

    // Tasks

    fun getTask(taskId: Long): Task = runBlocking(Dispatchers.IO) { db.taskDao().getTask(taskId) }

    fun getTasks(): Flow<List<Task>> = db.taskDao().getTasks()

    fun addTask(name: String, systemPrompt: String, modelId: Long) =
        runBlocking(Dispatchers.IO) {
            db.taskDao()
                .insertTask(Task(name = name, systemPrompt = systemPrompt, modelId = modelId))
        }

    fun deleteTask(taskId: Long) = runBlocking(Dispatchers.IO) { db.taskDao().deleteTask(taskId) }

    fun updateTask(task: Task) = runBlocking(Dispatchers.IO) { db.taskDao().updateTask(task) }

    // Folders

    fun getFolders(): Flow<List<Folder>> = db.folderDao().getFolders()

    fun addFolder(folderName: String) =
        runBlocking(Dispatchers.IO) { db.folderDao().insertFolder(Folder(name = folderName)) }

    fun updateFolder(folder: Folder) =
        runBlocking(Dispatchers.IO) { db.folderDao().updateFolder(folder) }

    /** Deletes the folder from the Folder table only */
    fun deleteFolder(folderId: Long) =
        runBlocking(Dispatchers.IO) {
            db.folderDao().deleteFolder(folderId)
            db.chatsDao().updateFolderIds(folderId, -1L)
        }

    /** Deletes the folder from the Folder table and corresponding chats from the Chat table */
    fun deleteFolderWithChats(folderId: Long) =
        runBlocking(Dispatchers.IO) {
            db.folderDao().deleteFolder(folderId)
            db.chatsDao().deleteChatsInFolder(folderId)
        }
}
