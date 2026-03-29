/**
 * TTS 服务 SSE 流式测试脚本
 * 测试 openai-edge-tts 服务的语音合成功能
 */

const http = require('http');
const fs = require('fs');

/**
 * 使用原生 HTTP 请求实现 SSE 流式 TTS
 * 详细展示 SSE 数据流的接收和处理过程
 */
async function streamTTSWithSSE(text, voice = 'zh-CN-XiaoxiaoNeural') {
  console.log('\n🎤 ========== SSE 流式模式 ==========');
  console.log(`📝 文本内容：${text}`);
  console.log(`🔊 语音类型：${voice}`);
  console.log('⏳ 开始接收流式数据...\n');

  return new Promise((resolve, reject) => {
    const postData = JSON.stringify({
      input: text,
      voice: voice,
      stream_format: 'sse',
      response_format: 'mp3',
    });

    const options = {
      hostname: 'localhost',
      port: 5050,
      path: '/v1/audio/speech',
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Content-Length': Buffer.byteLength(postData),
      },
    };

    const req = http.request(options, (res) => {
      console.log(`✅ HTTP 响应状态码：${res.statusCode}`);
      console.log(`📡 Content-Type: ${res.headers['content-type']}\n`);
      
      if (res.statusCode !== 200) {
        res.on('data', (chunk) => {
          console.error('❌ 错误响应:', chunk.toString());
        });
        reject(new Error(`HTTP 错误：${res.statusCode}`));
        return;
      }

      let audioChunks = [];
      let buffer = '';
      let chunkCount = 0;
      let totalBytes = 0;

      // 监听数据到达 - SSE 模式
      res.on('data', (chunk) => {
        const rawData = chunk.toString();
        console.log('📥 收到原始数据块:');
        console.log('--- 原始数据 ---');
        console.log(rawData.substring(0, 200) + (rawData.length > 200 ? '...' : ''));
        console.log('--- 结束 ---\n');
        
        buffer += rawData;
        
        // 处理 SSE 数据 - 按行分割
        const lines = buffer.split('\n');
        buffer = lines.pop() || ''; // 保留不完整的一行到下次处理

        for (const line of lines) {
          const trimmedLine = line.trim();
          
          // 跳过空行和注释
          if (!trimmedLine || trimmedLine.startsWith(':')) {
            continue;
          }
          
          console.log('📨 解析 SSE 行:', trimmedLine.substring(0, 80));
          
          if (trimmedLine.startsWith('data: ')) {
            try {
              const dataStr = trimmedLine.slice(6);
              const data = JSON.parse(dataStr);
              
              console.log('🔍 数据类型:', data.type);
              
              if (data.type === 'speech.audio.delta') {
                // 收到音频数据块
                const audioData = Buffer.from(data.audio, 'base64');
                audioChunks.push(audioData);
                chunkCount++;
                totalBytes += audioData.length;
                
                console.log(`📦 音频块 #${chunkCount}: ${(audioData.length / 1024).toFixed(2)} KB`);
                console.log(`   Base64 长度：${data.audio.length} 字符`);
                console.log(`   累计大小：${(totalBytes / 1024).toFixed(2)} KB\n`);
                
              } else if (data.type === 'speech.audio.done') {
                // 合成完成
                console.log('\n✅ ====== 语音合成完成 ======');
                console.log('📊 使用统计:', JSON.stringify(data.usage, null, 2));
                console.log(`📦 总块数：${chunkCount}`);
                console.log(`📏 总大小：${(totalBytes / 1024).toFixed(2)} KB\n`);
                
                // 合并所有音频块
                const combinedBuffer = Buffer.concat(audioChunks, totalBytes);
                
                // 保存到文件
                const outputPath = `test_sse_${Date.now()}.mp3`;
                fs.writeFileSync(outputPath, combinedBuffer);
                
                console.log(`💾 音频已保存到：${outputPath}`);
                console.log(`⏱️  可以播放测试了\n`);
                resolve(outputPath);
                
              } else if (data.type === 'error') {
                console.error('❌ 服务端错误:', data.error || data.message);
                reject(new Error(data.error || data.message));
              }
              
            } catch (error) {
              console.error('⚠️  JSON 解析失败:', error.message);
              console.error('   原始数据:', dataStr.substring(0, 100));
            }
          }
        }
      });

      res.on('end', () => {
        console.log('\n🔌 连接已结束');
        if (audioChunks.length > 0 && chunkCount > 0) {
          console.log('⚠️  未收到 done 信号，但已接收到音频数据');
          const combinedBuffer = Buffer.concat(audioChunks, totalBytes);
          const outputPath = `test_sse_incomplete_${Date.now()}.mp3`;
          fs.writeFileSync(outputPath, combinedBuffer);
          console.log(`💾 不完整的音频已保存到：${outputPath}`);
          resolve(outputPath);
        } else if (audioChunks.length === 0) {
          reject(new Error('未接收到任何音频数据'));
        }
      });
    });

    req.on('error', (error) => {
      console.error('\n❌ 请求失败:', error.message);
      reject(error);
    });

    console.log('📤 发送请求数据:');
    console.log(JSON.stringify(JSON.parse(postData), null, 2));
    console.log();
    
    req.write(postData);
    req.end();
  });
}

/**
 * 简单测试 - 非流式版本
 */
async function simpleTTS(text, voice = 'zh-CN-XiaoxiaoNeural') {
  console.log('🎤 开始合成语音（简单模式）...');
  
  const response = await fetch('http://localhost:5050/v1/audio/speech', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      input: text,
      voice: voice,
      response_format: 'mp3',
    }),
  });

  console.log(`✅ HTTP 响应状态码：${response.status}`);

  if (!response.ok) {
    const errorText = await response.text();
    throw new Error(`HTTP ${response.status}: ${errorText}`);
  }

  const arrayBuffer = await response.arrayBuffer();
  const buffer = Buffer.from(arrayBuffer);

  // 保存到文件
  const fs = require('fs');
  const outputPath = `test_output_${Date.now()}.mp3`;
  fs.writeFileSync(outputPath, buffer);

  console.log(`💾 音频已保存到：${outputPath}`);
  console.log(`📏 文件大小：${(buffer.length / 1024).toFixed(2)} KB`);
  
  return outputPath;
}

// ==================== 主函数 ====================
async function main() {
  try {
    console.log('╔════════════════════════════════════════════╗');
    console.log('║         🎵 TTS 服务流式测试脚本           ║');
    console.log('╚════════════════════════════════════════════╝');

    // 测试 SSE 流式模式
    console.log('\n【测试】SSE 流式模式 - 实时接收音频数据流\n');
    await simpleTTS('你好，欢迎使用语音合成服务。今天天气晴朗，适合外出活动。', 'zh-CN-XiaoxiaoNeural');
    
    console.log('\n✅ 测试完成！');

  } catch (error) {
    console.error('\n❌ 测试失败:', error.message);
    console.error('\n💡 提示：请确保 TTS 服务正在运行：docker-compose up -d tts');
    process.exit(1);
  }
}

// 运行测试
main();
