import websockets
import asyncio
import time
from json import dumps, loads
from collections import defaultdict

users: dict[str, dict[str, websockets.ServerConnection]] = defaultdict(dict)
users_lock = asyncio.Lock()

# 创建用户
async def create_user(websocket: websockets.ServerConnection, message_dict: dict[str, str]) -> bool:
    if "server" not in message_dict or "name" not in message_dict:
        print("Error: No 'server' or No 'name'")
        return False

    server = message_dict["server"]
    name = message_dict["name"]

    for existing_name, ws in users[server].items():
        try:
            await ws.send(dumps({
                "func": "create_user",
                "name": name
            }))
                        
            await websocket.send(dumps({
                "func": "create_user",
                "name": existing_name
            }))
        except websockets.ConnectionClosed:
            pass
    
    users[server][name] = websocket

    return True

# 发送消息
async def send_msg(message_dict: dict[str, str], user_info: tuple[str, str]) -> bool:
    if "msg" not in message_dict:
        print("Error: No 'msg'")
        return False
                
    msg = message_dict["msg"]
    async with users_lock:
        for ws in users[user_info[0]].values():
            try:
                await ws.send(dumps({
                    "func": "send_msg",
                    "name": user_info[1],
                    "msg": msg
                }))
            except websockets.ConnectionClosed:
                pass
    
    return True

# 删除用户
async def remove_user(user_info: tuple[str, str]):
    server, name = user_info

    print(f"Remove User '{name}' from Server '{server}'")

    async with users_lock:
        if server in users and name in users[server]:
            del users[server][name]
            for ws in users[server].values():
                try:
                    await ws.send(dumps({
                        "func": "remove_user",
                        "name": name
                    }))
                except websockets.ConnectionClosed:
                    pass

# websocket主进程
async def handler(websocket: websockets.ServerConnection):
    user_info = ("", "")
    message_count = 0
    start_time = time.time()
    last_message_time = 0
    
    try:
        async for message in websocket:
            # 限流检查
            current_time = time.time()
        
            if current_time - last_message_time < 0.1:
                print(f"Send Messages Too Quickly: {websocket.remote_address[0]}")
                await websocket.close(4001, "Send Messages Too Quickly")
                break
            
            message_count += 1
            elapsed = current_time - start_time
            
            if message_count > 100 and elapsed < 10:
                print(f"Send Messages Too Much: {websocket.remote_address[0]}")
                await websocket.close(4002, "Send Messages Too Much")
                break
            
            if elapsed > 10:
                message_count = 1
                start_time = current_time
            
            last_message_time = current_time

            print(f"IP: {websocket.remote_address[0]}")
            print(f"Message: {message}")

            # 解析json
            try:
                message_dict = loads(message)
            except Exception as e:
                print("Error:", e)
                continue
            
            # 处理消息
            if "func" not in message_dict:
                print("Error: No 'func'")
                continue
            
            func = message_dict["func"]

            if func == "create_user":
                # 创建用户
                if await create_user(websocket, message_dict):
                    user_info = (message_dict["server"], message_dict["name"])

            elif func == "send_msg":
                # 发送消息
                await send_msg(message_dict, user_info)
            
            print()

    except websockets.ConnectionClosed:
        pass

    except Exception as e:
        print("Error:", e)

    finally:
        # 删除用户
        await remove_user(user_info)
        
        print(f"Connection to {websocket.remote_address[0]} Closed")
        print()

# 启动websocket
async def main():
    async with websockets.serve(
        handler,
        "0.0.0.0",
        3473,
        max_size=1024*1024,
        max_queue=100,
        ping_interval=20,
        ping_timeout=20
    ) as server:
        print("BMW Server Started")
        print()
        
        await server.serve_forever()

if (__name__ == "__main__"):
    asyncio.run(main())
