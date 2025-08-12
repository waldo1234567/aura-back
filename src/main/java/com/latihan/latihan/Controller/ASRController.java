package com.latihan.latihan.Controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.vosk.Model;
import org.vosk.Recognizer;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/asr")
public class ASRController {
    private Model model;

    @PostConstruct
    public void initModel() throws IOException{
        try(InputStream in = getClass().getResourceAsStream("/model/vosk-model-small-en-us-0.15/")){
            File tempDir = new File(System.getProperty("java.io.tmpdir"), "vosk-model");
            if(!tempDir.exists()){
                tempDir.mkdirs();
                copyFolder(new File(getClass().getResource("/model/vosk-model-small-en-us-0.15").getFile()), tempDir);
            }
            this.model = new Model(tempDir.getAbsolutePath());
        }
    }

    @PostMapping(value = "/upload", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<String> transcribe(@RequestBody byte[] wavBytes){
        String tmpDir = System.getProperty("java.io.tmpdir");
        String rawPath = tmpDir + "/incoming_" + System.currentTimeMillis() + ".bin";
        String savedWavPath = tmpDir + "/incoming_" + System.currentTimeMillis() + ".wav";
        try {
            // Save raw bytes for inspection
            Files.write(Path.of(rawPath), wavBytes);
            System.out.println("Received bytes: " + wavBytes.length + " saved to " + rawPath);

            // Try to detect audio stream (may throw if not a supported audio format)
            try (ByteArrayInputStream bais = new ByteArrayInputStream(wavBytes)) {
                AudioInputStream ais = null;
                try {
                    ais = AudioSystem.getAudioInputStream(bais);
                    AudioFormat baseFormat = ais.getFormat();
                    System.out.println("AudioInputStream base format: " + baseFormat);

                    // Ensure we have PCM_SIGNED 16k mono
                    AudioFormat targetFormat = new AudioFormat(
                            AudioFormat.Encoding.PCM_SIGNED,
                            16000,
                            16,
                            1,
                            2,
                            16000,
                            false
                    );
                    AudioInputStream din = AudioSystem.getAudioInputStream(targetFormat, ais);
                    // Save converted stream to file for inspection
                    try (ByteArrayOutputStream bout = new ByteArrayOutputStream()) {
                        byte[] buf = new byte[4096];
                        int n;
                        while ((n = din.read(buf)) > 0) {
                            bout.write(buf, 0, n);
                        }
                        byte[] pcmBytes = bout.toByteArray();
                        logPcmDiagnostics(pcmBytes);
                        // Build a WAV header + the pcmBytes so you can play it
                        byte[] wavFile = buildWavFromPcm16(pcmBytes, 16000, 1);
                        Files.write(Path.of(savedWavPath), wavFile);
                        System.out.println("Converted PCM length: " + pcmBytes.length + " saved to " + savedWavPath);

                        // Feed to Vosk
                        try (Recognizer recognizer = new Recognizer(model, 16000)) {
                            int offset = 0;
                            int chunk = 4096;
                            while (offset < pcmBytes.length) {
                                int toRead = Math.min(chunk, pcmBytes.length - offset);
                                // create a small temp chunk buffer
                                byte[] slice = Arrays.copyOfRange(pcmBytes, offset, offset + toRead);
                                recognizer.acceptWaveForm(slice, slice.length);
                                System.out.println("partial: " + recognizer.getPartialResult());
                                offset += toRead;
                            }
                            String result = recognizer.getFinalResult(); // e.g. {"text":"..."}

                            try {
                                ObjectMapper om = new ObjectMapper();
                                // parse the Vosk JSON string to a Map so we return a real JSON object
                                Map<?,?> map = om.readValue(result, Map.class);
                                return ResponseEntity.ok()
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .body(map.toString());
                            } catch (Exception e) {
                                // If parsing fails, return the raw string but mark it as JSON (fallback)
                                return ResponseEntity.ok()
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .body(Collections.singletonMap("text", result).toString());
                            }
                        }
                    } finally {
                        try { din.close(); } catch (Exception ignored) {}
                    }
                } catch (UnsupportedAudioFileException uae) {
                    System.err.println("UnsupportedAudioFileException: " + uae.getMessage());
                    // Save raw bytes as .raw for inspection
                    Files.write(Path.of(savedWavPath + ".raw"), wavBytes);
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Unsupported audio format: " + uae.getMessage());
                } finally {
                    try { if (ais != null) ais.close(); } catch (Exception ignored) {}
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    private void logPcmDiagnostics(byte[] pcmBytes) {
        int samples = pcmBytes.length / 2;
        double sumSq = 0;
        int printN = Math.min(20, samples);
        System.out.print("first samples: ");
        for (int i = 0; i < printN; i++) {
            int lo = pcmBytes[i*2] & 0xff;
            int hi = pcmBytes[i*2+1];
            short s = (short)((hi<<8) | lo);
            System.out.print(s + " ");
            double v = s / 32768.0;
            sumSq += v * v;
        }
        double rms = Math.sqrt(sumSq / Math.max(1, samples));
        System.out.println("\nRMS=" + rms + " samples=" + samples);
    }

    private static byte[] buildWavFromPcm16(byte[] pcm16, int sampleRate, int channels) throws IOException {
        int byteRate = sampleRate * channels * 2;
        ByteBuffer bb = ByteBuffer.allocate(44 + pcm16.length).order(ByteOrder.LITTLE_ENDIAN);
        bb.put("RIFF".getBytes());
        bb.putInt(36 + pcm16.length);
        bb.put("WAVE".getBytes());
        bb.put("fmt ".getBytes());
        bb.putInt(16);
        bb.putShort((short)1); // PCM
        bb.putShort((short)channels);
        bb.putInt(sampleRate);
        bb.putInt(byteRate);
        bb.putShort((short)(channels * 2));
        bb.putShort((short)16);
        bb.put("data".getBytes());
        bb.putInt(pcm16.length);
        bb.put(pcm16);
        return bb.array();
    }

    private static void copyFolder(File src, File dest) throws IOException{
        if(src.isDirectory()){
            if(!dest.exists()) dest.mkdirs();
            for(String file : src.list()){
                copyFolder(new File(src, file), new File(dest, file));
            }
        }else{
            try (InputStream in = new FileInputStream(src);
                 OutputStream out = new FileOutputStream(dest)) {
                byte[] buffer = new byte[1024];
                int length;
                while ((length = in.read(buffer)) > 0) {
                    out.write(buffer, 0, length);
                }
            }
        }
    }

}
