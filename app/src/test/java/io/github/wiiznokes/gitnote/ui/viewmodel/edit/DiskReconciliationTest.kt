package io.github.wiiznokes.gitnote.ui.viewmodel.edit

import org.junit.Assert.assertEquals
import org.junit.Test

class DiskReconciliationTest {
    @Test
    fun `base diferente do disco com edicao local vira conflito`() {
        assertEquals(
            DiskReconciliation.Conflict,
            diskReconciliation(
                baseContent = "base",
                localContent = "minha edição",
                diskContent = "edição remota",
            ),
        )
    }

    @Test
    fun `base diferente do disco sem edicao local recarrega`() {
        assertEquals(
            DiskReconciliation.ReloadFromDisk,
            diskReconciliation(
                baseContent = "base",
                localContent = "base",
                diskContent = "edição remota",
            ),
        )
    }

    @Test
    fun `disco igual a base preserva edicao local`() {
        assertEquals(
            DiskReconciliation.Unchanged,
            diskReconciliation(
                baseContent = "base",
                localContent = "minha edição",
                diskContent = "base",
            ),
        )
    }
}
