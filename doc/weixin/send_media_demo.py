import os
import json
import uuid
import hashlib
import base64
import requests
import random
import time
import secrets
import io
from PIL import Image
from datetime import datetime
from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes
from cryptography.hazmat.primitives import padding

# ==============================================================================
# 工具路径配置 (用于将 MP3/WAV 转换为微信原生 SILK 格式)
# ==============================================================================
FFMPEG_EXE = r"D:\software\ffmpeg\bin\ffmpeg.exe"
SILK_ENCODER_EXE = r"D:\Project\silk-v3-decoder\windows\silk_v3_encoder.exe"

# ==============================================================================
# 自动加载配置
# ==============================================================================
CONFIG_FILE = "config.json"

def load_config():
    if not os.path.exists(CONFIG_FILE):
        raise FileNotFoundError(f"找不到 {CONFIG_FILE}，请先运行 bot.py 或手动创建")
    with open(CONFIG_FILE, "r", encoding="utf-8") as f:
        return json.load(f)

# ==============================================================================
# 转码与加密工具类
# ==============================================================================
import subprocess

def audio_to_silk(input_path):
    """自动将 MP3/WAV 转换为微信原生的 SILK 格式。"""
    ext = os.path.splitext(input_path)[1].lower()
    if ext == ".silk":
        return input_path, False # 不需要转换
    
    # 准备临时文件名
    uid = uuid.uuid4().hex[:8]
    pcm_path = f"temp_{uid}.pcm"
    silk_path = f"temp_{uid}.silk"
    
    print(f"[转码] 1. 正在使用 FFmpeg 转换为 PCM (24kHz Mono)...")
    try:
        # Step 1: MP3/WAV -> PCM (24kHz, 16bit, mono)
        # ffmpeg -i input.mp3 -f s16le -ar 24000 -ac 1 output.pcm
        subprocess.run([
            FFMPEG_EXE, "-y", "-i", input_path,
            "-f", "s16le", "-ar", "24000", "-ac", "1", pcm_path
        ], check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        
        print(f"[转码] 2. 正在使用 Silk-Encoder 转换为 SILK (-tencent)...")
        # Step 2: PCM -> SILK
        # silk_v3_encoder.exe input.pcm output.silk -tencent
        subprocess.run([
            SILK_ENCODER_EXE, pcm_path, silk_path, "-tencent"
        ], check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        
        # 清理 PCM
        if os.path.exists(pcm_path):
            os.remove(pcm_path)
            
        return silk_path, True # 返回 silk 路径并标记为临时文件
    except Exception as e:
        if os.path.exists(pcm_path): os.remove(pcm_path)
        if os.path.exists(silk_path): os.remove(silk_path)
        raise Exception(f"音频转码失败，请检查 FFmpeg 或 Encoder 路径: {e}")

def encrypt_aes_ecb(data: bytes, key: bytes) -> bytes:
    """使用 AES-128-ECB 加密并补全 PKCS7 填充。"""
    padder = padding.PKCS7(128).padder()
    padded_data = padder.update(data) + padder.finalize()
    cipher = Cipher(algorithms.AES(key), modes.ECB())
    encryptor = cipher.decryptor() # 等下，应该是 encryptor
    return cipher.encryptor().update(padded_data) + cipher.encryptor().finalize()

# 修正：Cipher 对象需要重新创建或者使用不同的方法调用
def encrypt_data(data: bytes, key: bytes) -> bytes:
    padder = padding.PKCS7(128).padder()
    padded_data = padder.update(data) + padder.finalize()
    cipher = Cipher(algorithms.AES(key), modes.ECB())
    encryptor = cipher.encryptor()
    return encryptor.update(padded_data) + encryptor.finalize()

def get_md5(data: bytes) -> str:
    return hashlib.md5(data).hexdigest()

# ==============================================================================
# 微信 iLink API 客户端
# ==============================================================================
class WechatMediaSender:
    def __init__(self, cfg):
        self.cfg = cfg
        self.bot_token = cfg.get("bot_token", "")
        self.bot_user_id = cfg.get("bot_user_id") or self.bot_token.split(":")[0]
        self.base_url = cfg.get("bot_base_url", "https://ilinkai.weixin.qq.com")
        
        # 核心协议头增强 (必须与 bot.py 保持 100% 一致)
        # X-WECHAT-UIN: 随机 uint32 -> 字符串 -> base64
        random_uint32 = random.randint(0, 0xFFFFFFFF)
        random_uin = base64.b64encode(str(random_uint32).encode()).decode()
        
        self.headers = {
            "Authorization": f"Bearer {self.bot_token}",
            "AuthorizationType": "ilink_bot_token",
            "Content-Type": "application/json",
            "X-WECHAT-UIN": random_uin
        }

    def api_post(self, path, body):
        url = f"{self.base_url.rstrip('/')}/{path}"
        res = requests.post(url, json=body, headers=self.headers)
        return res.json()

    def get_latest_user(self):
        """从本地配置读取最近活跃的用户 (由 bot.py 更新)。"""
        user_id = self.cfg.get("last_user_id")
        ctx_token = self.cfg.get("last_context_token")
        
        if not user_id or not ctx_token:
            print("[错误] 没有找到预存的目标用户。")
            print("请先给机器人发个消息，让它在 config.json 中自动记录您的身份，然后再运行此脚本。")
            raise ValueError("缺失 last_user_id 或 last_context_token")
            
        print(f"[1] 找到预存的目标用户: {user_id}")
        return user_id, ctx_token

    def send_media_msg(self, to_user_id, ctx_token, item_type, item_data):
        """构建 sendmessage 包并发送。完全对齐 bot.py 的成功参数。"""
        print(f"[3] 正在发送消息包 (MsgType: {item_type})...")
        client_id = f"openclaw-weixin-{random.randint(0, 0xFFFFFFFF):08x}"
        payload = {
            "msg": {
                "from_user_id": "", # 对齐 bot.py
                "to_user_id": to_user_id,
                "client_id": client_id,
                "message_type": 2, # BOT 发出
                "message_state": 2, # 对齐 bot.py
                "context_token": ctx_token,
                "item_list": [{
                    "type": item_type,
                    **item_data
                }]
            },
            "base_info": {"channel_version": "2.1.3"} # 对齐 @tencent-weixin/openclaw-weixin 2.1.3
        }
        res = self.api_post("ilink/bot/sendmessage", payload)
        ret_code = res.get("ret", 0)
        if ret_code != 0:
            print(f"    -> [错误详情] {res}") # 打印完整 JSON 响应以供调优
            ret_msg = f"失败(错误码: {ret_code}, 消息: {res.get('errmsg', '无')})"
        else:
            ret_msg = "成功"
        print(f"    -> 发送结果: {ret_msg}")
        return res

    def upload_media(self, file_path, media_type, to_user_id):
        """通用 CDN 上传逻辑 (加密 -> 预签名 -> 上传 -> 获取凭证)"""
        print(f"[2] 准备上传媒体文件: {os.path.basename(file_path)} (Type: {media_type})")
        if not os.path.exists(file_path):
            raise FileNotFoundError(f"文件不存在: {file_path}")

        # 1. 准备加密原文件
        with open(file_path, "rb") as f:
            raw_data = f.read()
        
        aes_key = os.urandom(16) # 随机生成 16 字节密钥
        encrypted_data = encrypt_data(raw_data, aes_key)
        
        # 2. 如果是图片或视频，准备缩略图
        thumb_raw_data = None
        thumb_encrypted_data = None
        if media_type in (1, 2): # IMAGE or VIDEO
            print("    -> 正在生成缩略图...")
            try:
                if media_type == 1: # 图片缩略图
                    with Image.open(file_path) as img:
                        # 转换为 RGB 模式（防止 RGBA 报错）
                        if img.mode != 'RGB':
                            img = img.convert('RGB')
                        img.thumbnail((256, 256)) # 微信缩略图一般较小
                        thumb_io = io.BytesIO()
                        img.save(thumb_io, format='JPEG', quality=70)
                        thumb_raw_data = thumb_io.getvalue()
                else: # 视频缩略图 (这里简单用一张空白图代替，实际应该截取视频第一帧)
                    img = Image.new('RGB', (256, 256), color = 'black')
                    thumb_io = io.BytesIO()
                    img.save(thumb_io, format='JPEG')
                    thumb_raw_data = thumb_io.getvalue()
                    
                thumb_encrypted_data = encrypt_data(thumb_raw_data, aes_key)
            except Exception as e:
                print(f"    -> [警告] 生成缩略图失败: {e}，将尝试不带缩略图上传")
                thumb_raw_data = None
                thumb_encrypted_data = None

        # 2. 获取上传 URL (getuploadurl)
        # UploadMediaType: IMAGE: 1, VIDEO: 2, FILE: 3, VOICE: 4
        pre_req = {
            "filekey": secrets.token_hex(16),
            "media_type": media_type,
            "to_user_id": to_user_id,
            "rawsize": len(raw_data),
            "rawfilemd5": get_md5(raw_data),
            "filesize": len(encrypted_data),
            "no_need_thumb": True,
            "aeskey": aes_key.hex()
        }
        
        # 移除缩略图相关的参数，因为官方源码中并没有使用缩略图
        # if thumb_raw_data and thumb_encrypted_data:
        #     pre_req.update({
        #         "thumb_rawsize": len(thumb_raw_data),
        #         "thumb_rawfilemd5": get_md5(thumb_raw_data),
        #         "thumb_filesize": len(thumb_encrypted_data)
        #     })

        pre_res = self.api_post("ilink/bot/getuploadurl", pre_req)
        upload_url = pre_res.get("upload_full_url")
        if not upload_url:
            upload_url = pre_res.get("upload_param") # 兼容处理
            
        thumb_upload_url = pre_res.get("thumb_upload_full_url")
        if not thumb_upload_url:
            thumb_upload_url = pre_res.get("thumb_upload_param") # 兼容处理
        
        if not upload_url:
            raise Exception(f"获取上传 URL 失败: {pre_res.get('errmsg', '未知错误')}")

        # 4. 执行原文件上传
        print(f"    -> 正在上传原文件加密流至 CDN (Size: {len(encrypted_data)} bytes)...")
        cdn_headers = {"Content-Type": "application/octet-stream"}
        
        # 如果 upload_url 是完整的 URL，直接 POST；如果是参数，可能需要拼接
        if upload_url.startswith("http"):
            cdn_res = requests.post(upload_url, data=encrypted_data, headers=cdn_headers)
        else:
            # 假设需要拼接，这里可能需要根据实际情况调整
            raise Exception("upload_url 不是完整的 URL，请检查 API 返回格式")
            
        if cdn_res.status_code != 200:
            raise Exception(f"原文件 CDN 上传失败，状态码: {cdn_res.status_code}")
        
        # 4. 获取下载凭证 (x-encrypted-param)
        download_param = cdn_res.headers.get("x-encrypted-param")
        if not download_param:
            # 如果响应头中没有，尝试从响应体中获取（如果响应是 JSON）
            try:
                cdn_res_json = cdn_res.json()
                download_param = cdn_res_json.get("x-encrypted-param")
            except:
                pass
                
        if not download_param:
            print(f"    -> [警告] 原文件 CDN 响应中缺失 x-encrypted-param，响应头: {cdn_res.headers}")
            # 官方源码中，如果上传成功，可能会直接使用某些参数作为凭证
            # 这里我们暂时抛出异常，以便进一步调试
            raise Exception("原文件 CDN 响应中缺失 x-encrypted-param")
            
        # 5. 执行缩略图上传 (已移除)
        thumb_download_param = None

        print(f"    -> 上传成功，获得凭证: {download_param[:30]}...")
        
        # 官方源码中，aes_key 被转换为 hex 字符串，然后再 base64 编码
        # 这是一个非常关键的细节，如果不一致会导致微信客户端解密失败（图片显示为黑色）
        aes_key_hex = aes_key.hex()
        aes_key_b64 = base64.b64encode(aes_key_hex.encode('utf-8')).decode('utf-8')
        
        return download_param, thumb_download_param, aes_key_b64, len(encrypted_data)

# ==============================================================================
# Demo 开跑
# ==============================================================================
def main():
    try:
        cfg = load_config()
        client = WechatMediaSender(cfg)
        
        # 自动定位最新用户
        user_id, ctx_token = client.get_latest_user()

        print("\n" + "!"*50)
        print(" 重要提示：请确保 bot.py 已停止运行，否则会产生 Session 冲突！")
        print("!"*50)

        print("\n" + "="*50)
        print("       微信 ClawBot 多媒体发送演示工具 (增强版)")
        print("="*50)
        print(" 0. 发送测试文本 (验证连通性)")
        print(" 1. 发送语音 (Voice) - 支持 mp3, wav, silk (自动转码)")
        print(" 2. 发送普通文件/图片 (File/Image) - 自动检测格式")
        print(" 3. 发送视频 (Video) - mp4 视频")
        print(" 0. 退出程序")
        print("="*50)

        choice = input("\n请选择发送类型 [0-3]: ").strip()
        
        if choice == "" or choice == "exit":
            print("退出程序。")
            return
        
        # 0. 发送测试文本
        if choice == "0":
            test_text = f"演示工具连通性测试 - {datetime.now().strftime('%H:%M:%S')}"
            client.send_media_msg(user_id, ctx_token, 1, {"text_item": {"text": test_text}})
            return
        
        if choice not in ("1", "2", "3"):
            print("无效输入，退出程序。")
            return

        file_path = input("请粘贴要发送文件的【绝对路径】: ").strip().strip('"').strip("'")

        if not os.path.exists(file_path):
            print(f"  [错误] 找不到文件: {file_path}")
            return

        ext = os.path.splitext(file_path)[1].lower()

        # --- 处理分发 ---
        
        # 1. 发送语音
        if choice == "1":
            silk_path, is_temp = audio_to_silk(file_path)
            with open(silk_path, "rb") as f:
                raw_data = f.read()
            
            # 估算时长：1KB 约 1.25s (1250ms)
            play_time = int(len(raw_data) / 1024 * 1250)
            file_md5 = get_md5(raw_data)
            
            # 先尝试原生语音消息发送
            print("[智能探测] 尝试原生语音消息发送...")
            try:
                param, thumb_param, key, encrypted_size = client.upload_media(silk_path, 4, user_id)
                res = client.send_media_msg(user_id, ctx_token, 3, {
                    "voice_item": {
                        "media": {
                            "encrypt_query_param": param, 
                            "aes_key": key,
                            "encrypt_type": 1
                        },
                        "encode_type": 6,
                        "bits_per_sample": 16,
                        "playtime": play_time,
                        "sample_rate": 24000
                    }
                })
                if res.get("ret", 0) != 0:
                    raise Exception(f"语音消息发送失败: ret={res.get('ret')}")
            except Exception as e:
                print(f"    -> [失败降级] 原生语音发送失败({e})，正在转为\"文件模式\"重试...")
                param, thumb_param, key, encrypted_size = client.upload_media(silk_path, 3, user_id)
                client.send_media_msg(user_id, ctx_token, 4, {
                    "file_item": {
                        "media": {
                            "encrypt_query_param": param, 
                            "aes_key": key,
                            "encrypt_type": 1
                        },
                        "file_name": os.path.basename(silk_path),
                        "md5": file_md5,
                        "len": str(len(raw_data))
                    }
                })
            
            if is_temp and os.path.exists(silk_path):
                os.remove(silk_path)

        # 2. 发送文件或图片 (智能检测 + 自动降级)
        elif choice == "2":
            with open(file_path, "rb") as f:
                raw_data = f.read()
            file_md5 = get_md5(raw_data)
            
            if ext in (".jpg", ".jpeg", ".png", ".gif"):
                print(f"[智能探测] 检测到图片格式 {ext}，优先尝试原生图片消息发送...")
                try:
                    # 尝试以 IMAGE (Type 1) 上传
                    param, thumb_param, key, encrypted_size = client.upload_media(file_path, 1, user_id)
                    
                    # 组装图片消息
                    image_item = {
                        "media": {
                            "encrypt_query_param": param, 
                            "aes_key": key,
                            "encrypt_type": 1
                        },
                        "mid_size": encrypted_size # 必须包含密文大小
                    }
                    # 官方源码中并没有使用 thumb_media
                    # if thumb_param:
                    #     image_item["thumb_media"] = {"encrypt_query_param": thumb_param, "aes_key": key}
                        
                    client.send_media_msg(user_id, ctx_token, 2, {
                        "image_item": image_item
                    })
                except Exception as e:
                    print(f"    -> [失败降级] 原生图片上传失败({e})，正在自动转为“文件模式”重试...")
                    param, thumb_param, key, encrypted_size = client.upload_media(file_path, 3, user_id)
                    client.send_media_msg(user_id, ctx_token, 4, {
                        "file_item": {
                            "media": {
                                "encrypt_query_param": param, 
                                "aes_key": key,
                                "encrypt_type": 1
                            },
                            "file_name": os.path.basename(file_path),
                            "md5": file_md5,
                            "len": str(len(raw_data))
                        }
                    })
            else:
                print(f"[智能探测] 文件格式，以通用文件形式发送...")
                param, thumb_param, key, encrypted_size = client.upload_media(file_path, 3, user_id)
                client.send_media_msg(user_id, ctx_token, 4, {
                    "file_item": {
                        "media": {
                            "encrypt_query_param": param, 
                            "aes_key": key,
                            "encrypt_type": 1
                        },
                        "file_name": os.path.basename(file_path),
                        "md5": file_md5,
                        "len": str(len(raw_data))
                    }
                })

        # 3. 发送视频 (智能检测 + 自动降级)
        elif choice == "3":
            with open(file_path, "rb") as f:
                raw_data = f.read()
            video_md5 = get_md5(raw_data)
            print(f"[智能探测] 优先尝试原生视频消息发送...")
            
            # 微信原生视频消息通常只支持 MP4 格式
            if ext != ".mp4":
                print(f"    -> [警告] 微信原生视频通常只支持 MP4 格式，当前格式为 {ext}，可能会发送失败或无法播放")
                
            try:
                # 尝试以 VIDEO (Type 2) 上传
                param, thumb_param, key, encrypted_size = client.upload_media(file_path, 2, user_id)
                
                video_item = {
                    "media": {
                        "encrypt_query_param": param, 
                        "aes_key": key,
                        "encrypt_type": 1
                    },
                    "play_length": 5000, 
                    "video_md5": video_md5,
                    "video_size": encrypted_size
                }
                # if thumb_param:
                #     video_item["thumb_media"] = {"encrypt_query_param": thumb_param, "aes_key": key}
                    
                client.send_media_msg(user_id, ctx_token, 5, {
                    "video_item": video_item
                })
            except Exception as e:
                print(f"    -> [失败降级] 原生视频上传失败({e})，正在自动转为“文件模式”重试...")
                param, thumb_param, key, encrypted_size = client.upload_media(file_path, 3, user_id)
                client.send_media_msg(user_id, ctx_token, 4, {
                    "file_item": {
                        "media": {
                            "encrypt_query_param": param, 
                            "aes_key": key,
                            "encrypt_type": 1
                        },
                        "file_name": os.path.basename(file_path),
                        "md5": video_md5,
                        "len": str(len(raw_data))
                    }
                })

        print("\n" + "-"*50)
        print("[完成] 消息已尝试发送，请检查手机微信。")
        print("-"*50)

    except Exception as e:
        print(f"\n" + "!"*50)
        print(f"[报错] {e}")
        print("!"*50)

if __name__ == "__main__":
    main()
