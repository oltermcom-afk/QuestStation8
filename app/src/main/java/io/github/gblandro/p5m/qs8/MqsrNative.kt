// SPDX-License-Identifier: AGPL-3.0-only
package io.github.gblandro.p5m.qs8

/** Most do `qs8_mqsr.cpp`: MQSR (eksperymentalne), działa od razu, także w trakcie gry. */
object MqsrNative
{
	init { System.loadLibrary("p5m-vr") }

	external fun nativeSetMqsr(on: Boolean)
}
