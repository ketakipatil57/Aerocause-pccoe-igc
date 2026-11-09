-- AeroCause Flyway Migration V5
-- Seed: 4 synthetic stations for Vayu Nagar (imaginary city)
-- and placeholder official documents.
-- All station data is SYNTHETIC.
-- Uses MERGE INTO for H2 compatibility (also works with MySQL via Flyway MySQL dialect).

MERGE INTO stations (external_id, name, city, lat, lon, provider)
    KEY (external_id)
    VALUES ('STN-001', 'Central Monitor', 'Vayu Nagar', 18.5204, 73.8567, 'SYNTHETIC');

MERGE INTO stations (external_id, name, city, lat, lon, provider)
    KEY (external_id)
    VALUES ('STN-002', 'North Station', 'Vayu Nagar', 18.5500, 73.8400, 'SYNTHETIC');

MERGE INTO stations (external_id, name, city, lat, lon, provider)
    KEY (external_id)
    VALUES ('STN-003', 'East Station', 'Vayu Nagar', 18.5100, 73.9000, 'SYNTHETIC');

MERGE INTO stations (external_id, name, city, lat, lon, provider)
    KEY (external_id)
    VALUES ('STN-004', 'South Station', 'Vayu Nagar', 18.4900, 73.8600, 'SYNTHETIC');

-- Official guidance documents (titles only; actual text loaded into ChromaDB by AI service)
-- IMPORTANT: Verify these document references against official sources before use.
MERGE INTO documents (doc_key, title, publisher, version, url)
    KEY (doc_key)
    VALUES ('CPCB-AQI-2014',
            'Technical Document on Revised National Ambient Air Quality Standards',
            'CPCB (Central Pollution Control Board)', '2014', 'https://cpcb.nic.in/');

MERGE INTO documents (doc_key, title, publisher, version, url)
    KEY (doc_key)
    VALUES ('CPCB-GRADED-RESPONSE-2017',
            'Graded Response Action Plan (GRAP) for Delhi-NCR',
            'CPCB / Supreme Court Committee', '2017', 'https://cpcb.nic.in/');

MERGE INTO documents (doc_key, title, publisher, version, url)
    KEY (doc_key)
    VALUES ('WHO-AQG-2021',
            'WHO Global Air Quality Guidelines 2021',
            'World Health Organization', '2021',
            'https://www.who.int/publications/i/item/9789240034228');

MERGE INTO documents (doc_key, title, publisher, version, url)
    KEY (doc_key)
    VALUES ('AEROCAUSE-QC-GUIDE-V1',
            'AeroCause Internal QC Rule Reference (Heuristic)',
            'AeroCause Project Team', '0.1', NULL);
