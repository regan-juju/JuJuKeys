package com.reganbarua.jujulauncher

import org.junit.Assert.*
import org.junit.Test

class OpsTest {
    private fun keys(n: Int) = (1..n).map { "p$it/A" }

    @Test fun firstRunFillsPagesAndDock() {
        val l = Ops.reconcile(LayoutState(listOf(emptyList()), emptyList()), keys(60), listOf("p1/A", "p2/A", "zzz/A"))
        assertEquals(listOf("p1/A", "p2/A"), l.dock)
        assertEquals(3, l.pages.size)
        assertEquals(24, l.pages[0].size)
        assertEquals(58 - 48, l.pages[2].size)
    }

    @Test fun uninstalledAreRemovedAndNewAppended() {
        var l = Ops.reconcile(LayoutState(listOf(emptyList()), emptyList()), keys(5), emptyList())
        l = Ops.reconcile(l, listOf("p1/A", "p3/A", "new/A"), emptyList())
        assertEquals(listOf("p1/A", "p3/A", "new/A"), l.pages[0].map { it.id })
    }

    @Test fun hiddenAppsStayHiddenButStayInstalled() {
        var l = Ops.reconcile(LayoutState(listOf(emptyList()), emptyList()), keys(3), emptyList())
        l = Ops.hide(l, "p2/A")
        assertEquals(listOf("p1/A", "p3/A"), l.pages[0].map { it.id })
        l = Ops.reconcile(l, keys(3), emptyList())
        assertEquals(2, l.pages[0].size)
        l = Ops.addToHome(l, "p2/A")
        assertEquals("p2/A", l.pages[0].last().id)
    }

    @Test fun dropOnAppMakesFolderThenAddsMore() {
        var l = Ops.reconcile(LayoutState(listOf(emptyList()), emptyList()), keys(4), emptyList())
        l = Ops.drop(l, AppItem("p1/A"), 0, 1, "p2/A") { "f1" }
        val f = l.pages[0][0] as FolderItem
        assertEquals(listOf("p2/A", "p1/A"), f.keys)
        l = Ops.drop(l, AppItem("p4/A"), 0, 0, "f1") { "f2" }
        assertEquals(listOf("p2/A", "p1/A", "p4/A"), (l.pages[0][0] as FolderItem).keys)
        assertEquals(listOf("f1", "p3/A").size, l.pages[0].size)
    }

    @Test fun leaveFolderCollapsesSingle() {
        var l = LayoutState(listOf(listOf(FolderItem("f", "F", listOf("a/A", "b/A")))), emptyList())
        l = Ops.leaveFolder(l, "f", "a/A")
        assertEquals(listOf("b/A", "a/A"), l.pages[0].map { it.id })
        assertTrue(l.pages[0].all { it is AppItem })
    }

    @Test fun reorderSamePage() {
        var l = Ops.reconcile(LayoutState(listOf(emptyList()), emptyList()), keys(5), emptyList())
        l = Ops.drop(l, AppItem("p1/A"), 0, 4, null)   // drag first to slot 4 (display index)
        assertEquals(listOf("p2/A", "p3/A", "p4/A", "p1/A", "p5/A"), l.pages[0].map { it.id })
    }

    @Test fun overflowMovesToNextPage() {
        var l = Ops.reconcile(LayoutState(listOf(emptyList()), emptyList()), keys(24), emptyList())
        l = Ops.drop(l, AppItem("x/A"), 0, 0, null)
        assertEquals(2, l.pages.size)
        assertEquals("x/A", l.pages[0][0].id)
        assertEquals("p24/A", l.pages[1][0].id)
    }

    @Test fun dockOverflowReturnsToHome() {
        var l = Ops.reconcile(LayoutState(listOf(emptyList()), emptyList()), keys(8), listOf("p1/A", "p2/A", "p3/A", "p4/A"))
        l = Ops.toDock(l, "p5/A", 0)
        assertEquals(4, l.dock.size)
        assertEquals("p5/A", l.dock[0])
        assertTrue(l.pages[0].any { it.id == "p4/A" })
    }
}
