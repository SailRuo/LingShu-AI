Usage
Endpoint: /v1/audio/speech
Generates audio from the input text. Available parameters:

Required Parameter:

input (string): The text to be converted to audio (up to 4096 characters).
Optional Parameters:

model (string): Set to "tts-1" or "tts-1-hd" (default: "tts-1").
voice (string): One of the OpenAI-compatible voices (alloy, echo, fable, onyx, nova, shimmer) or any valid edge-tts voice (default: "en-US-AvaNeural").
response_format (string): Audio format. Options: mp3, opus, aac, flac, wav, pcm (default: mp3).
speed (number): Playback speed (0.25 to 4.0). Default is 1.0.
stream_format (string): Response format. Options: "audio" (raw audio data, default) or "sse" (Server-Sent Events streaming with JSON events).
Note: The API is fully compatible with OpenAI's TTS API specification. The instructions parameter (for fine-tuning voice characteristics) is not currently supported, but all other parameters work identically to OpenAI's implementation.

Standard Audio Generation
Example request with curl and saving the output to an mp3 file:

curl -X POST http://localhost:5050/v1/audio/speech \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer your_api_key_here" \
  -d '{
    "input": "Hello, I am your AI assistant! Just let me know how I can help bring your ideas to life.",
    "voice": "echo",
    "response_format": "mp3",
    "speed": 1.1
  }' \
  --output speech.mp3
Direct Audio Playback (like OpenAI)
You can pipe the audio directly to ffplay for immediate playback, just like OpenAI's API:

curl -X POST http://localhost:5050/v1/audio/speech \
  -H "Authorization: Bearer your_api_key_here" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "tts-1",
    "input": "Today is a wonderful day to build something people love!",
    "voice": "alloy",
    "response_format": "mp3"
  }' | ffplay -i -
Or for immediate playback without saving to file:

curl -X POST http://localhost:5050/v1/audio/speech \
  -H "Authorization: Bearer your_api_key_here" \
  -H "Content-Type: application/json" \
  -d '{
    "input": "This will play immediately without saving to disk!",
    "voice": "shimmer"
  }' | ffplay -autoexit -nodisp -i -
Or, to be in line with the OpenAI API endpoint parameters:

curl -X POST http://localhost:5050/v1/audio/speech \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer your_api_key_here" \
  -d '{
    "model": "tts-1",
    "input": "Hello, I am your AI assistant! Just let me know how I can help bring your ideas to life.",
    "voice": "alloy"
  }' \
  --output speech.mp3
Server-Sent Events (SSE) Streaming
For applications that need structured streaming events (like web applications), use SSE format:

curl -X POST http://localhost:5050/v1/audio/speech \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer your_api_key_here" \
  -d '{
    "model": "tts-1",
    "input": "This will stream as Server-Sent Events with JSON data containing base64-encoded audio chunks.",
    "voice": "alloy",
    "stream_format": "sse"
  }'
SSE Response Format:

data: {"type": "speech.audio.delta", "audio": "base64-encoded-audio-chunk"}

data: {"type": "speech.audio.delta", "audio": "base64-encoded-audio-chunk"}

data: {"type": "speech.audio.done", "usage": {"input_tokens": 12, "output_tokens": 0, "total_tokens": 12}}
JavaScript/Web Usage
Example using fetch API for SSE streaming:

async function streamTTSWithSSE(text) {
  const response = await fetch('http://localhost:5050/v1/audio/speech', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: 'Bearer your_api_key_here',
    },
    body: JSON.stringify({
      input: text,
      voice: 'alloy',
      stream_format: 'sse',
    }),
  });

  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  const audioChunks = [];

  while (true) {
    const { done, value } = await reader.read();
    if (done) break;

    const chunk = decoder.decode(value);
    const lines = chunk.split('\n');

    for (const line of lines) {
      if (line.startsWith('data: ')) {
        const data = JSON.parse(line.slice(6));

        if (data.type === 'speech.audio.delta') {
          // Decode base64 audio chunk
          const audioData = atob(data.audio);
          const audioArray = new Uint8Array(audioData.length);
          for (let i = 0; i < audioData.length; i++) {
            audioArray[i] = audioData.charCodeAt(i);
          }
          audioChunks.push(audioArray);
        } else if (data.type === 'speech.audio.done') {
          console.log('Speech synthesis complete:', data.usage);

          // Combine all chunks and play
          const totalLength = audioChunks.reduce(
            (sum, chunk) => sum + chunk.length,
            0
          );
          const combinedArray = new Uint8Array(totalLength);
          let offset = 0;
          for (const chunk of audioChunks) {
            combinedArray.set(chunk, offset);
            offset += chunk.length;
          }

          const audioBlob = new Blob([combinedArray], { type: 'audio/mpeg' });
          const audioUrl = URL.createObjectURL(audioBlob);
          const audio = new Audio(audioUrl);
          audio.play();
          return;
        }
      }
    }
  }
}

// Usage
streamTTSWithSSE('Hello from SSE streaming!');
International Language Example
And an example of a language other than English:

curl -X POST http://localhost:5050/v1/audio/speech \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer your_api_key_here" \
  -d '{
    "model": "tts-1",
    "input": "じゃあ、行く。電車の時間、調べておくよ。",
    "voice": "ja-JP-KeitaNeural"
  }' \
  --output speech.mp3
JavaScript/Web Usage
Example using fetch API for SSE streaming:

async function streamTTSWithSSE(text) {
  const response = await fetch('http://localhost:5050/v1/audio/speech', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: 'Bearer your_api_key_here',
    },
    body: JSON.stringify({
      input: text,
      voice: 'alloy',
      stream_format: 'sse',
    }),
  });

  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  const audioChunks = [];

  while (true) {
    const { done, value } = await reader.read();
    if (done) break;

    const chunk = decoder.decode(value);
    const lines = chunk.split('\n');

    for (const line of lines) {
      if (line.startsWith('data: ')) {
        const data = JSON.parse(line.slice(6));

        if (data.type === 'speech.audio.delta') {
          // Decode base64 audio chunk
          const audioData = atob(data.audio);
          const audioArray = new Uint8Array(audioData.length);
          for (let i = 0; i < audioData.length; i++) {
            audioArray[i] = audioData.charCodeAt(i);
          }
          audioChunks.push(audioArray);
        } else if (data.type === 'speech.audio.done') {
          console.log('Speech synthesis complete:', data.usage);

          // Combine all chunks and play
          const totalLength = audioChunks.reduce(
            (sum, chunk) => sum + chunk.length,
            0
          );
          const combinedArray = new Uint8Array(totalLength);
          let offset = 0;
          for (const chunk of audioChunks) {
            combinedArray.set(chunk, offset);
            offset += chunk.length;
          }

          const audioBlob = new Blob([combinedArray], { type: 'audio/mpeg' });
          const audioUrl = URL.createObjectURL(audioBlob);
          const audio = new Audio(audioUrl);
          audio.play();
          return;
        }
      }
    }
  }
}

// Usage
streamTTSWithSSE('Hello from SSE streaming!');
Additional Endpoints
POST/GET /v1/models: Lists available TTS models.
POST/GET /v1/voices: Lists edge-tts voices for a given language / locale.
POST/GET /v1/voices/all: Lists all edge-tts voices, with language support information.
Contributing
Contributions are welcome! Please fork the repository and create a pull request for any improvements.