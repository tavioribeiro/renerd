package com.example.renerd.components.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.text.Html
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.SeekBar
import androidx.annotation.RequiresApi
import androidx.constraintlayout.widget.ConstraintLayout
import coil.load
import com.example.renerd.R
import com.example.renerd.core.utils.formatTime
import com.example.renerd.core.utils.log
import com.example.renerd.databinding.BottomSheetLayoutBinding
import com.example.renerd.services.AudioService3
import com.example.renerd.view_models.EpisodeViewModel
import com.google.android.material.bottomsheet.BottomSheetBehavior
import core.extensions.changeBackgroundColorWithGradient
import core.extensions.cropCenterSection
import core.extensions.darkenColor
import core.extensions.fadeInAnimation
import core.extensions.getPalletColors
import core.extensions.getSizes
import core.extensions.resize
import core.extensions.startSkeletonAnimation
import core.extensions.stopSkeletonAnimation
import core.extensions.toAllRoundedDrawable
import org.koin.java.KoinJavaComponent.inject


class FloatingPlayer @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr), FloatingPlayerContract.View {

    private lateinit var bottomSheetBehavior: BottomSheetBehavior<View>
    private var onExpandedCallback: (() -> Unit)? = null
    private var onCollapsedCallback: (() -> Unit)? = null

    private val binding: BottomSheetLayoutBinding = BottomSheetLayoutBinding.inflate(LayoutInflater.from(context), this, true)
    private val presenter: FloatingPlayerContract.Presenter by inject(clazz = FloatingPlayerContract.Presenter::class.java)

    private var isPlaying = false

    private var currentEpisode = EpisodeViewModel()

    init {
        binding.mainContainer.visibility = View.GONE

        presenter.attachView(this)
        presenter.getCurrentPlayingEpisode()
    }


    override fun showUi(){
        binding.mainContainer.fadeInAnimation {
            binding.mainContainer.visibility = View.VISIBLE
        }
    }

    override fun updateInfosUi(episode: EpisodeViewModel){
        // Atualizar a UI do player
        binding.miniPlayerTitle.text = episode.title
        binding.mainPlayerTitle.text = episode.title

        binding.mainPlayerDescription.text = Html.fromHtml("${episode.description}")
        binding.miniPlayerProduct.text = episode.product


        binding.miniPlayerPoster.startSkeletonAnimation(20f)
        binding.mainPlayerPoster.startSkeletonAnimation(20f)


        binding.miniPlayerPoster.load(episode.imageUrl){
            target(

                onSuccess = { drawable ->
                    //Define a imagem com borda curva e para o skeleton
                    binding.miniPlayerPoster.getSizes{ width, height ->
                        val crop = drawable.cropCenterSection(widthDp = width, heightDp = height, resources)

                        binding.miniPlayerPoster.setImageDrawable(crop.toAllRoundedDrawable(20f))
                        binding.miniPlayerPoster.stopSkeletonAnimation()

                    }



                    //Define a imagem com borda curva e para o skeleton
                    binding.mainPlayerPoster.getSizes{ width, height ->
                        val resized = drawable.resize(width = width, height = height, resources)

                        binding.mainPlayerPoster.setImageDrawable(resized.toAllRoundedDrawable(20f))
                        binding.mainPlayerPoster.stopSkeletonAnimation()
                    }



                    //Obter a paleta de cores da imagem
                    binding.mainPlayerPoster.getPalletColors { colors ->
                        val (color1, color2) = colors
                        try {
                            binding.mainContainer.changeBackgroundColorWithGradient(
                                color1 = darkenColor(color1, 90.0),
                                color2 = darkenColor(color2, 70.0)
                            )
                        } catch (e: Exception) {
                            log(e)
                        }
                    }
                },
                onError = {
                    binding.miniPlayerPoster.setImageResource(R.drawable.background)
                    binding.mainPlayerPoster.setImageResource(R.drawable.background)
                }
            )
        }
    }

    override fun updateCurrentEpisode(episode: EpisodeViewModel){
        currentEpisode = episode
    }

    private val playerStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == PLAYER_STATUS_UPDATE) {
                val isPlaying = intent.getBooleanExtra(IS_PLAYING, false)
                val currentTime = intent.getIntExtra(CURRENT_TIME, 0)
                val totalTime = intent.getIntExtra(TOTAL_TIME, 0)

                updatePlayPauseButtonUi(isPlaying, currentTime, totalTime)

                updateDatabase(isPlaying, currentTime, totalTime)

                log("\n\nFloating Player playerStatusReceiver currentEpisode: ${currentEpisode.title} | ${currentEpisode.elapsedTime}")
            }
        }
    }





    @RequiresApi(Build.VERSION_CODES.O)
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        this.setupBottomSheet {
            collapse()
        }
        this.setUpTouch()

        val intentFilter = IntentFilter(FloatingPlayer.PLAYER_STATUS_UPDATE)
        context.registerReceiver(playerStatusReceiver, intentFilter, Context.RECEIVER_EXPORTED)
    }



    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        context.unregisterReceiver(playerStatusReceiver)
    }



    private fun updatePlayerTimerUI(currentTime: Int, totalTime: Int) {
        binding.mainPlayerCurrentTime.text = formatTime(currentTime)
        binding.mainPlayerTotalTime.text = formatTime(totalTime)
        binding.mainPlayerSeekBar.max = totalTime
        binding.mainPlayerSeekBar.progress = currentTime
    }



    private fun setupBottomSheet(onInitialized: () -> Unit) {
        bottomSheetBehavior = BottomSheetBehavior.from(this)
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        bottomSheetBehavior.peekHeight = binding.miniPlayer.layoutParams.height
        bottomSheetBehavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                when (newState) {
                    BottomSheetBehavior.STATE_EXPANDED -> onExpandedCallback?.invoke()
                    BottomSheetBehavior.STATE_COLLAPSED -> onCollapsedCallback?.invoke()
                }
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                binding.miniPlayer.alpha = 1 - slideOffset
                binding.mainPlayer.alpha = slideOffset
            }
        })
        onInitialized()
    }



    private fun setUpTouch() {

        binding.miniPlayer.setOnClickListener {
            this.expand()
        }

        binding.miniPlayerPlayPauseButton.setOnClickListener {
            playPauseClicked()
        }

        binding.mainPlayerPlayPauseButton.setOnClickListener {
            playPauseClicked()
        }

        binding.mainPlayerSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) seekTo(progress)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        binding.buttomJumpTo.setOnClickListener(){
            this.seekTo(currentEpisode.jumpToTime * 1000)
        }
    }



    private fun playPauseClicked() {
        val intent = Intent(context, AudioService3::class.java)
        if (isPlaying) {
            intent.action = "PAUSE"
            isPlaying = false
        } else {
            intent.action = "PLAY"
            isPlaying = true
        }


        log("")
        log("--------------------------")
        log("\n\nFloating Player playPauseClicked currentEpisode: ${currentEpisode.title} | ${currentEpisode.elapsedTime}")

        log(context.packageName)
        intent.putExtra("id", currentEpisode.id)
        intent.putExtra("title", currentEpisode.title)
        intent.putExtra("product", currentEpisode.product)
        intent.putExtra("audioUrl", currentEpisode.audioUrl)
        intent.putExtra("imageUrl", currentEpisode.imageUrl)
        intent.putExtra("elapsedTime", currentEpisode.elapsedTime)

        context.startService(intent)
    }



    private fun seekTo(position: Int) {
        val intent = Intent(context, AudioService3::class.java)
        intent.action = "SEEK_TO"
        intent.putExtra("position", position)
        context.startService(intent)
    }



    fun startEpisode(episode: EpisodeViewModel) {
        currentEpisode = episode

        presenter.setCurrentPlayingEpisodeId(episode)


        this.updatePlayPauseButtonUi(isPlaying, episode.elapsedTime.toInt(), episode.duration.toInt())
        this.showUi()


        val intent = Intent(context, AudioService3::class.java)
        intent.action = "PLAY"
        intent.putExtra("id", episode.id)
        intent.putExtra("title", episode.title)
        intent.putExtra("product", episode.product)
        intent.putExtra("audioUrl", episode.audioUrl)
        intent.putExtra("imageUrl", episode.imageUrl)
        intent.putExtra("elapsedTime", episode.elapsedTime)

        log("")
        log("--------------------------")
        log("\n\nFloating Player startEpisode currentEpisode: ${currentEpisode.title} | ${currentEpisode.elapsedTime}")

        context.startService(intent)

        // Atualizar a UI do player
        updateInfosUi(episode)

        isPlaying = true
    }


    private fun updateDatabase(isPlaying: Boolean, currentTime: Int, totalTime: Int) {
        this.isPlaying = isPlaying
        if(isPlaying) {
            currentEpisode.elapsedTime = currentTime
            currentEpisode.duration = totalTime
            presenter.updateEpisode(currentEpisode)
        }
    }


     override fun updatePlayPauseButtonUi(isPlaying: Boolean, currentTime: Int, totalTime: Int) {
        this.isPlaying = isPlaying
        if (isPlaying) {
            binding.miniPlayerPlayPauseButton.setImageResource(R.drawable.icon_pause)
            binding.mainPlayerPlayPauseButton.setIconResource(R.drawable.icon_pause)
            binding.mainPlayerPlayPauseButton.setLabel("Pause")
        } else {
            binding.miniPlayerPlayPauseButton.setImageResource(R.drawable.icon_play)
            binding.mainPlayerPlayPauseButton.setIconResource(R.drawable.icon_play)
            binding.mainPlayerPlayPauseButton.setLabel("Play")
        }
        updatePlayerTimerUI(currentTime, totalTime)
    }








    fun expand() {
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
    }

    fun setOnExpandedCallback(callback: () -> Unit) {
        this.onExpandedCallback = callback
    }

    fun collapse() {
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
    }

    fun setOnCollapsedCallback(callback: () -> Unit) {
        this.onCollapsedCallback = callback
    }






    fun stopService() {
        val intent = Intent(context, AudioService3::class.java)
        intent.action = "STOP"
        context.startService(intent)
    }


    companion object {
        const val PLAYER_STATUS_UPDATE = "com.example.renerd.components.player.PLAYER_STATUS_UPDATE"
        const val IS_PLAYING = "isPlaying"
        const val CURRENT_TIME = "currentTime"
        const val TOTAL_TIME = "totalTime"
    }
}