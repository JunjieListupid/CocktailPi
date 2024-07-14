package net.alex9849.cocktailpi.model.eventaction;

import jakarta.persistence.DiscriminatorValue;

import javax.sound.sampled.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;

@DiscriminatorValue("PlayAudio")
public class PlayAudioEventAction extends FileEventAction {
    private boolean onRepeat;
    private int volume;
    private String soundDevice;

    public boolean isOnRepeat() {
        return onRepeat;
    }

    public void setOnRepeat(boolean onRepeat) {
        this.onRepeat = onRepeat;
    }

    public int getVolume() {
        return volume;
    }

    public void setVolume(int volume) {
        this.volume = volume;
    }

    public String getSoundDevice() {
        return soundDevice;
    }

    public void setSoundDevice(String soundDevice) {
        this.soundDevice = soundDevice;
    }

    @Override
    public String getDescription() {
        String desc = "Play audiofile: " + getFileName() + " (Volume: " + this.volume + "%)";
        if(this.onRepeat) {
            desc += " (Repeating)";
        }
        return desc;
    }

    @Override
    public void trigger(RunningAction runningAction) {
        CountDownLatch syncLatch = new CountDownLatch(1);
        Clip clip = null;

        try {
            clip = getClipForSoundDevice();
            if (clip == null) {
                throw new IllegalStateException("Sound device \"" + this.soundDevice + "\" not found!");
            }

            setClipListener(clip, syncLatch);
            setVolume(clip);
            playClip(clip, syncLatch);
        } catch (Exception e) {
            handleExceptions(clip, e, runningAction);
        } finally {
            if (clip != null) {
                clip.stop();
                clip.close();
            }
        }
    }

    private Clip getClipForSoundDevice() throws LineUnavailableException {
        Line.Info sourceInfo = new Line.Info(SourceDataLine.class);
        for (Mixer.Info info : AudioSystem.getMixerInfo()) {
            Mixer mixer = AudioSystem.getMixer(info);
            if (!mixer.isLineSupported(sourceInfo)) {
                continue;
            }
            if (!Objects.equals(mixer.getMixerInfo().getName(), this.soundDevice)) {
                continue;
            }
            return AudioSystem.getClip(info);
        }
        return null;
    }

    private void setClipListener(Clip clip, CountDownLatch syncLatch) {
        clip.addLineListener(e -> {
            if (e.getType() == LineEvent.Type.STOP) {
                syncLatch.countDown();
            }
        });
    }

    private void setVolume(Clip clip) {
        FloatControl floatControl = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
        if (floatControl != null) {
            float volume = ((floatControl.getMaximum() - floatControl.getMinimum()) / 100 * this.volume) + floatControl.getMinimum();
            volume = Math.min(volume, floatControl.getMaximum());
            volume = Math.max(volume, floatControl.getMinimum());
            floatControl.setValue(volume);
        }
    }

    private void playClip(Clip clip, CountDownLatch syncLatch) throws InterruptedException {
        clip.loop(onRepeat ? Clip.LOOP_CONTINUOUSLY : 0);
        clip.start();
        syncLatch.await();
    }

    private void handleExceptions(Clip clip, Exception e, RunningAction runningAction) {
        e.printStackTrace();
        runningAction.addLog(e);
        if (clip != null) {
            clip.stop();
            clip.close();
        }
    }

}
