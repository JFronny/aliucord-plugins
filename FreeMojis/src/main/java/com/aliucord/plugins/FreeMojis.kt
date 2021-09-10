package com.aliucord.plugins

import android.content.Context
import android.view.View
import com.aliucord.annotations.AliucordPlugin
import kotlin.Throws
import com.discord.widgets.emoji.WidgetEmojiSheet
import top.canyie.pine.callback.MethodHook
import top.canyie.pine.Pine.CallFrame
import com.discord.utilities.textprocessing.node.EmojiNode.EmojiIdAndType.Custom
import com.discord.databinding.WidgetEmojiSheetBinding
import android.view.ViewGroup
import com.discord.restapi.RestAPIParams
import com.discord.models.domain.NonceGenerator
import com.discord.utilities.time.ClockFactory
import com.discord.utilities.rest.RestAPI
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.aliucord.Utils
import com.aliucord.entities.Plugin
import com.aliucord.utils.DimenUtils
import com.aliucord.utils.RxUtils.createActionSubscriber
import com.aliucord.utils.RxUtils.subscribe
import com.aliucord.views.Button
import com.discord.models.guild.Guild
import com.discord.stores.StoreStream

@AliucordPlugin
class FreeMojis : Plugin() {
    override fun start(context: Context) {
        try {
            val layoutId = View.generateViewId()
            val getEmojiIdAndType = WidgetEmojiSheet::class.java.getDeclaredMethod("getEmojiIdAndType")
            getEmojiIdAndType.isAccessible = true
            val getBinding = WidgetEmojiSheet::class.java.getDeclaredMethod("getBinding")
            getBinding.isAccessible = true
            patcher.patch(WidgetEmojiSheet::class.java.getDeclaredMethod("configureButtons", Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType, Guild::class.java), object : MethodHook() {
                @Throws(Throwable::class)
                override fun afterCall(callFrame: CallFrame) {
                    super.afterCall(callFrame)
                    val args = callFrame.args
                    val _this = callFrame.thisObject as WidgetEmojiSheet
                    val emoji = getEmojiIdAndType.invoke(_this) as Custom
                    val url = "https://cdn.discordapp.com/emojis/" + emoji.id + if (emoji.isAnimated) ".gif" else ".png"
                    val binding = getBinding.invoke(_this) as WidgetEmojiSheetBinding
                    val root = binding.root as ViewGroup
                    val rootLayout = root.getChildAt(0) as LinearLayout
                    if (rootLayout.findViewById<View?>(layoutId) != null) return
                    val ctx = rootLayout.context ?: return
                    val marginDpFour = DimenUtils.dpToPx(4)
                    val marginDpEight = marginDpFour * 2
                    val marginDpSixteen = marginDpEight * 2
                    val sendRawButton = Button(ctx)
                    sendRawButton.text = "Send as Image"
                    sendRawButton.setOnClickListener {
                        val message = RestAPIParams.Message(
                                url,  // Content
                                NonceGenerator.computeNonce(ClockFactory.get()).toString(),  // Nonce
                                null,  // ApplicationId
                                null,  // Activity
                                emptyList(),  // stickerIds
                                null,  // messageReference
                                RestAPIParams.Message.AllowedMentions(
                                        emptyList(),  // parse
                                        emptyList(),  //users
                                        emptyList(),  // roles
                                        false // repliedUser
                                )
                        )

                        Utils.threadPool.execute {
                            RestAPI.api.sendMessage(StoreStream.getChannelsSelected().id, message).subscribe(createActionSubscriber({ }))
                            _this.dismiss()
                        }
                    }
                    val buttonParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT)
                    buttonParams.setMargins(0, 0, 0, 0)
                    sendRawButton.layoutParams = buttonParams
                    val pluginButtonLayout = com.aliucord.widgets.LinearLayout(ctx)
                    pluginButtonLayout.id = layoutId
                    pluginButtonLayout.addView(sendRawButton)
                    val idx = 2
                    if ((args[0] == false // not nitro
                                    || args[1] == false // not on server
                                    ) && args[2] != null // belongs to a guild
                    ) {
                        // Nitro or Join Button visible
                        pluginButtonLayout.setPadding(marginDpSixteen, marginDpFour, marginDpSixteen, marginDpEight)

                        // Adjust nitro and join button
                        val params = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT)
                        params.setMargins(0, 0, 0, 0)
                        binding.q.layoutParams = params // Nitro
                        binding.o.layoutParams = params // Join

                        // Adjust nitro/join container
                        val joinContainer = binding.o.parent as FrameLayout
                        val containerParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                        containerParams.setMargins(marginDpSixteen, marginDpEight, marginDpSixteen, 0)
                        joinContainer.layoutParams = containerParams
                        rootLayout.addView(pluginButtonLayout, idx)
                    }
                }
            })
        }
        catch (e : Throwable) {
            Utils.showToast(context, e.message);
        }
    }

    // Called when your plugin is stopped
    override fun stop(context: Context) = patcher.unpatchAll()
}