package com.reganbarua.jujukeys.translation

/** Plain UI-state + callbacks bundle handed to [TranslatePanel] from the IME service. */
data class TranslateUiState(
    val sourceText: String = "",
    val resultText: String = "",
    val hint: String? = null,
    val direction: TranslationDirection = TranslationDirection.BN_TO_EN,
    val onSwapDirection: () -> Unit = {},
    val onTranslate: () -> Unit = {},
    val onClear: () -> Unit = {},
    val onInsertResult: () -> Unit = {}
)
