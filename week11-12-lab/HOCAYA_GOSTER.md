# Öğretmene gösterim sırası — Weeks 11–12

Önce README.md içindeki kurulum ve testleri çalıştır. Ekran görüntülerini `evidence/` klasöründe tut.

1. `docker compose ps`: Kafka, PostgreSQL, order-service (8080) ve payment-service (Docker içinde 8081).
2. `docker compose exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --describe --topic orders`: üç partition.
3. `.\scripts\task1-offsets.ps1`: 9 mesaj ve A/B/C partition-offset tablosu.
4. `.\scripts\task2-ordering.ps1`: keyed sıralı, key olmadan RoundRobinPartitioner ve kontrollü processing delay ile sırası bozulabilen müşteri. `ORDER_CONSUMED` loglarında partition, offset, key, seq.
5. `.\scripts\task3-crash.ps1`: raw mode aynı offset tekrar işlenir ve 2 satır oluşur; safe mode `processed_event` PRIMARY KEY ile 1 satır kalır. `Runtime.halt(1)` kasıtlıdır.
6. `.\scripts\task4-5-payments.ps1`: 10 saniyelik baseline, 2 saniyelik timeout; 3 deneme / 500ms→1000ms exponential backoff; 400'e retry yok; Idempotency-Key ile ödeme sayısı 1.
7. `.\scripts\task6-circuit.ps1`: 30 çağrı, CLOSED/OPEN/HALF_OPEN/CLOSED; actuator event'leri, OPEN yanıt süresini timeout ile karşılaştır.
8. Gerçek çıktılar ve ekran görüntülerinden **3–5 sayfalık PDF rapor** oluştur; `Lab_Week11_12_Surname_Name.pdf`.

**Önemli:** Bu kod paketi henüz senin bilgisayarında çalıştırılmadı. Öğretmene gerçek başarı sonuçlarını ancak adım adım Docker ve Maven testlerinden sonra göster.
