package com.local.dailyautomation.runtime

import org.junit.Assert.assertEquals
import org.junit.Test
import org.mozilla.javascript.Context
import java.io.File

/** Node does not reproduce the bundled Rhino interpreter's loop-const bug. */
class RhinoPollingTest {
    private fun evaluate(asset: String, setup: String, exercise: String): String {
        Context.enter().use { context ->
            context.languageVersion = Context.VERSION_ES6
            context.isInterpretedMode = true
            val scope = context.initStandardObjects()
            val source = File("src/assistant/assets/project/core/$asset.js").readText()
            val script = """
                var module = { exports: {} };
                var now = 0;
                Date.now = function() { return now; };
                function sleep(ms) { now += ms; }
                $setup
                $source
                $exercise
            """.trimIndent()
            return Context.toString(context.evaluateString(scope, script, "polling-$asset", 1, null))
        }
    }

    @Test fun `visual postcondition reads later probes and exits immediately on success`() {
        assertEquals("COMPLETED_TODAY:500:3", evaluate("vision", "", """
            var calls = 0;
            var result = waitFor(function() {
                calls += 1;
                return { state: calls < 3 ? 'NOT_COMPLETED' : 'COMPLETED_TODAY' };
            }, 'COMPLETED_TODAY', 8000);
            result.state + ':' + now + ':' + calls;
        """))
    }

    @Test fun `unknown visual state retains the full timeout`() {
        assertEquals("null:8000", evaluate("vision", "", """
            var result = waitFor(function() { return { state: 'UNKNOWN' }; }, 'COMPLETED_TODAY', 8000);
            String(result) + ':' + now;
        """))
    }

    @Test fun `text and exact node waits accept content that loads later`() {
        assertEquals("true:750:true:600:true:750:true:600", evaluate("selectors", """
            function classNameMatches() { return { find: function() {
                return now < 600 ? [] : [{text:function(){return '已签';},desc:function(){return '';} }];
            } }; }
            function text() { return {findOnce:function(){return now < 600 ? null : {ready:true};}}; }
            function desc() { return {findOnce:function(){return null;}}; }
        """, """
            var a = waitForAnyText(['已签'], 15000) + ':' + now;
            now = 0;
            var b = waitForAllTexts(['已签'], 15000) + ':' + now;
            now = 0;
            var c = waitForAnyTextContaining(['已'], 15000) + ':' + now;
            now = 0;
            var d = !!waitForExactNode('已签', 15000) + ':' + now;
            a + ':' + b + ':' + c + ':' + d;
        """))
    }

    @Test fun `containing node wait searches later markers and later frames`() {
        assertEquals("true:600", evaluate("selectors", """
            function textContains(label) { return {findOnce:function(){
                return now >= 600 && label === '擦亮成功' ? {ready:true} : null;
            }}; }
            function descContains() { return {findOnce:function(){return null;}}; }
        """, """
            !!waitForAnyNodeContaining(['今日已擦亮', '擦亮成功'], 15000) + ':' + now;
        """))
    }

    @Test fun `shortcut wait observes delayed foreground and waits for business readiness`() {
        assertEquals("true:3000", evaluate("navigation", """
            var selectorsStub = { firstNodeContaining:function(){return null;} };
            function require(name) { return name.indexOf('selectors') >= 0 ? selectorsStub : {}; }
            var device = {width:1440,height:3200};
            var launched = false;
            function home() { launched = false; }
            function currentPackage() { return launched && now >= 2000 ? 'com.tencent.mm' : 'com.miui.home'; }
            var icon = {bounds:function(){return {left:100,top:300,right:300,bottom:600,
                width:function(){return 200;},height:function(){return 300;}};},
                clickable:function(){return true;},click:function(){launched=true;return true;},
                desc:function(){return '湖南大学微生活';}};
            function desc() { return {findOnce:function(){return icon;}}; }
            function text() { return {findOnce:function(){return null;}}; }
        """, """
            openWechatDesktopShortcut(['湖南大学微生活'], [], function(){return now >= 3000;}) + ':' + now;
        """))
    }

    @Test fun `merged mine entry waits for late markers without retaining the first null`() {
        assertEquals("true", evaluate("navigation", """
            var page = 'launcher';
            var selectorsStub = {
                exactNode:function(){return null;}, firstExactNode:function(){return null;},
                firstNodeContaining:function(label){
                    if(page === 'published' && (label === '我发布的' || label === '在卖')) return {};
                    if(page === 'mine' && now >= 6000 && (label === '我的交易' || label === '我发布的')) return {};
                    return null;
                }
            };
            function require(name){return name.indexOf('selectors') >= 0 ? selectorsStub : {};}
            var device = {width:1440,height:3200};
            function home(){page='launcher';}
            function currentPackage(){return page === 'launcher' ? 'com.miui.home' : 'com.taobao.idlefish';}
            var icon = {bounds:function(){return {left:100,top:300,right:300,bottom:600,
                width:function(){return 200;},height:function(){return 300;}};},
                clickable:function(){return true;},click:function(){page='home';return true;},
                desc:function(){return '闲鱼';}};
            function desc(){return {findOnce:function(){return icon;}};}
            function text(){return {findOnce:function(){return null;}};}
            function click(x,y){if(y > 2900) page='mine'; else page='published'; return true;}
        """, """
            var opened = openXianyuPublishedItems();
            String(opened && page === 'published' && now >= 6300 && now < 6600);
        """))
    }
}
