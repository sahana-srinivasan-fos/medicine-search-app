import socket
import json
import time
import sys

# Try importing pyautogui, provide installation instructions if missing
try:
    import pyautogui
except ImportError:
    print("Error: 'pyautogui' module not found.")
    print("Please install it using: pip install pyautogui")
    sys.exit(1)

# Configuration
PORT = 5001  # Default port used by the Android app

def get_local_ip():
    """Gets the local IP address of this computer."""
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        # Doesn't need to be reachable, just triggers OS routing query
        s.connect(('10.255.255.255', 1))
        ip = s.getsockname()[0]
    except Exception:
        ip = '127.0.0.1'
    finally:
        s.close()
    return ip

def type_items(items):
    """Simulates keyboard strokes to type each item at the active cursor."""
    print(f"\n[Typing] Starting typing process for {len(items)} items...")
    
    # 2-second buffer to make sure the user is focused on the target window
    print("Focus on the target text field now (starting in 1 second)...")
    time.sleep(1.0)
    
    for i, item in enumerate(items):
        print(f" -> Typing item {i + 1}: {item}")
        # Type the text character by character
        pyautogui.write(item)
        
        # Press Enter key to submit/go to the next line
        pyautogui.press('enter')
        
        # Small delay between items to allow target app to register inputs
        time.sleep(0.2)
        
    print("[Typing] Finished successfully.")

def execute_instructions(instructions):
    """Simulates keyboard actions based on custom instructions list."""
    print(f"\n[Typing] Executing {len(instructions)} custom keyboard instructions...")
    print("Focus on the target text field now (starting in 1 second)...")
    time.sleep(1.0)
    
    for inst in instructions:
        action = inst.get("action")
        if action == "type":
            text = inst.get("text", "")
            print(f" -> Typing: {text}")
            pyautogui.write(text)
        elif action == "press":
            key = inst.get("key", "")
            print(f" -> Pressing: {key}")
            pyautogui.press(key)
        elif action == "wait":
            seconds = inst.get("seconds", 0.1)
            time.sleep(seconds)
        time.sleep(0.05) # short gap between actions
        
    print("[Typing] Custom instructions executed successfully.")

def start_server():
    local_ip = get_local_ip()
    
    # Create TCP Socket
    server_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server_socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    
    try:
        server_socket.bind(('', PORT))
        server_socket.listen(5)
    except Exception as e:
        print(f"Error binding to port {PORT}: {e}")
        return

    print("=" * 60)
    print("            PHARMACAM DESKTOP KEYBOARD SERVER")
    print("=" * 60)
    print(f"Server is listening on:")
    print(f"  IP Address : {local_ip}")
    print(f"  Port       : {PORT}")
    print(f"Enter this IP address '{local_ip}:{PORT}' in your Android app settings.")
    print("=" * 60)
    print("Waiting for confirmed scans from Android app...\n")

    try:
        while True:
            client_socket, client_address = server_socket.accept()
            print(f"[Connection] Incoming scan confirmed from {client_address[0]}")
            
            data_buffer = bytearray()
            while True:
                chunk = client_socket.recv(1024)
                if not chunk:
                    break
                data_buffer.extend(chunk)
                
            client_socket.close()

            if not data_buffer:
                print("[Warning] Connected, but no payload was received.")
                continue

            try:
                payload = json.loads(data_buffer.decode('utf-8'))
                instructions = payload.get("instructions", [])
                items = payload.get("items", [])
                
                if instructions:
                    execute_instructions(instructions)
                elif items:
                    print(f"[Received] Medicines (Legacy Mode): {items}")
                    type_items(items)
                else:
                    print("[Warning] Received payload contains no items or instructions.")
                    
            except json.JSONDecodeError:
                print(f"[Error] Failed to parse JSON. Raw data: {data_buffer.decode('utf-8', errors='ignore')}")
            except Exception as e:
                print(f"[Error] Processing payload failed: {e}")
                
    except KeyboardInterrupt:
        print("\nShutting down server. Goodbye!")
    finally:
        server_socket.close()

if __name__ == "__main__":
    # Safety feature: Move mouse cursor to top-left corner (0,0) to abort typing if needed
    pyautogui.FAILSAFE = True
    start_server()