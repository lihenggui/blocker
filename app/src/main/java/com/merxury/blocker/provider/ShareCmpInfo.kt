package com.merxury.blocker.provider

data class ShareCmpInfo(
  val pkg: String,
  val components: List<Component>
) {
  data class Component(
    val type: String,
    val name: String,
    val block: Boolean
  )
}
