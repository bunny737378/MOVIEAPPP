package com.lagradost.cloudstream3.ui.settings.extensions

import com.fasterxml.jackson.annotation.JsonProperty
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

const val REPOSITORIES_KEY = "REPOSITORIES_KEY"

@Serializable
data class RepositoryData(
    @JsonProperty("iconUrl") @SerialName("iconUrl") val iconUrl: String? = null,
    @JsonProperty("name") @SerialName("name") val name: String,
    @JsonProperty("url") @SerialName("url") val url: String,
    @JsonProperty("description") @SerialName("description") val description: String? = null,
) {
    /**
     * Exact 3-argument constructor matching upstream CloudStream3 `RepositoryData(iconUrl, name, url)`.
     * Required by meta-repo plugins like MegaProvider.cs3 which link directly against `(String, String, String)V`.
     */
    constructor(iconUrl: String?, name: String, url: String) : this(iconUrl, name, url, null)
}
