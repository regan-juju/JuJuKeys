package com.reganbarua.jujukeys.emoji

object EmojiData {
    data class Category(val label: String, val emojis: List<String>)

    val categories: List<Category> = listOf(
        Category(
            "Smileys",
            listOf(
                "😀", "😃", "😄", "😁", "😆", "😅", "🤣", "😂", "🙂", "🙃",
                "😉", "😊", "😇", "🥰", "😍", "🤩", "😘", "😗", "😚", "😙",
                "😋", "😛", "😜", "🤪", "😝", "🤑", "🤗", "🤭", "🤫", "🤔"
            )
        ),
        Category(
            "Gestures",
            listOf(
                "👍", "👎", "👌", "🤞", "🤟", "🤘", "👏", "🙌", "👐", "🤲",
                "🙏", "✌️", "🤙", "👋", "💪", "🫡", "🤝", "👆", "👇", "☝️"
            )
        ),
        Category(
            "Hearts",
            listOf(
                "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍", "🤎", "💔",
                "❣️", "💕", "💞", "💓", "💗", "💖", "💘", "💝"
            )
        ),
        Category(
            "Objects",
            listOf(
                "🔥", "⭐", "🌟", "✨", "🎉", "🎊", "🎈", "🎁", "📱", "💻",
                "⌚", "📷", "🔑", "💡", "📌", "📎", "✏️", "📝", "📚", "☕"
            )
        ),
        Category(
            "Nature",
            listOf(
                "🌸", "🌼", "🌻", "🌹", "🌳", "🌴", "🌙", "☀️", "⛅", "🌧️",
                "🌈", "❄️", "🐶", "🐱", "🐦", "🐟", "🦋", "🌊"
            )
        )
    )
}
