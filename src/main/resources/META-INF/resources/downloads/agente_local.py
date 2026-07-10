import time
import requests
import threading
import sys
from datetime import datetime

try:
    from scapy.all import sniff, IP, TCP, UDP, ICMP, IPv6
except ImportError:
    print("ERRO: Scapy não instalado. Execute: pip install scapy")
    sys.exit(1)

try:
    import requests
except ImportError:
    print("ERRO: Requests não instalado. Execute: pip install requests")
    sys.exit(1)

# ==============================================================================
# CONFIGURAÇÕES DO AGENTE
# ==============================================================================
# Substitua pela URL da sua VPS (ou localhost se testando local)
API_URL = "http://localhost:8080/trafego/api/ingest"

# O token deve bater com a variável de ambiente: framework.trafego.ingest-token 
TOKEN = "minha-chave-secreta" 
# ==============================================================================

pacotes_buffer = []
lock = threading.Lock()

def formatar_flags_tcp(F):
    flags = []
    if F & 0x02: flags.append("SYN")
    if F & 0x10: flags.append("ACK")
    if F & 0x01: flags.append("FIN")
    if F & 0x04: flags.append("RST")
    if F & 0x08: flags.append("PSH")
    if F & 0x20: flags.append("URG")
    return ",".join(flags) if flags else str(F)

def processar_pacote(pkt):
    try:
        # Analisa IPv4 ou IPv6
        ip_layer = pkt.getlayer(IP) or pkt.getlayer(IPv6)
        if not ip_layer:
            return
            
        ip_src = ip_layer.src
        ip_dst = ip_layer.dst
        
        proto = "IP"
        sp = None
        dp = None
        info = ""
        
        if TCP in pkt:
            proto = "TCP"
            sp = pkt[TCP].sport
            dp = pkt[TCP].dport
            info = "Flags: " + formatar_flags_tcp(pkt[TCP].flags)
        elif UDP in pkt:
            proto = "UDP"
            sp = pkt[UDP].sport
            dp = pkt[UDP].dport
            if dp == 53 or sp == 53:
                proto = "DNS"
                info = "DNS Query/Response"
        elif ICMP in pkt:
            proto = "ICMP"
            info = f"Type: {pkt[ICMP].type}"
            
        # Omitimos tráfego gerado pelo próprio agente para não causar loop infinito
        if "8080" in str(dp) or "8080" in str(sp):
            return
            
        p = {
            "timestamp": datetime.now().strftime("%H:%M:%S"),
            "protocolo": proto,
            "origem": ip_src,
            "destino": ip_dst,
            "portaOrigem": sp,
            "portaDestino": dp,
            "tamanho": len(pkt),
            "info": info
        }
        with lock:
            pacotes_buffer.append(p)
    except Exception as e:
        pass

def enviar_batch():
    while True:
        time.sleep(1) # envia a cada 1 segundo (evita DDoS na VPS)
        with lock:
            if not pacotes_buffer:
                continue
            # Limita a 30 pacotes para não estourar o buffer e manter em tempo real
            batch = pacotes_buffer[-30:]
            pacotes_buffer.clear()
            
        payload = {
            "pacotes": batch,
            "wifi": [], # Omitido no script didático simplificado
            "bluetooth": []
        }
        
        try:
            requests.post(API_URL, json=payload, headers={"X-Trafego-Token": TOKEN}, timeout=2)
            print(f"[{datetime.now().strftime('%H:%M:%S')}] {len(batch)} pacotes enviados para {API_URL}")
        except Exception as e:
            print(f"[{datetime.now().strftime('%H:%M:%S')}] Erro de conexão com VPS: {e}")

if __name__ == '__main__':
    print("="*60)
    print(" A&D FRAMEWORK - Agente Local de Captura ")
    print("="*60)
    print(f" Destino: {API_URL}")
    print(f" Lembre-se de rodar como Administrador/Root!")
    print("="*60)
    
    t = threading.Thread(target=enviar_batch, daemon=True)
    t.start()
    
    print("\nIniciando captura de pacotes (Pressione Ctrl+C para parar)...")
    try:
        sniff(prn=processar_pacote, store=False)
    except Exception as e:
        print(f"ERRO CRÍTICO no Scapy (falta Npcap/WinPcap ou root?): {e}")
