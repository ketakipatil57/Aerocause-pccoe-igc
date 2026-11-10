-- Seed the imaginary Vayu Nagar monitoring stations and document metadata.
-- Source text is not bundled here; approved files are user-managed in ai-service/corpus.

INSERT INTO stations (external_id, name, city, lat, lon, provider)
VALUES ('STN-001', 'Central Monitor', 'Vayu Nagar', 18.5204, 73.8567, 'SYNTHETIC')
ON DUPLICATE KEY UPDATE name=VALUES(name), city=VALUES(city), lat=VALUES(lat),
    lon=VALUES(lon), provider=VALUES(provider);

INSERT INTO stations (external_id, name, city, lat, lon, provider)
VALUES ('STN-002', 'North Station', 'Vayu Nagar', 18.5500, 73.8400, 'SYNTHETIC')
ON DUPLICATE KEY UPDATE name=VALUES(name), city=VALUES(city), lat=VALUES(lat),
    lon=VALUES(lon), provider=VALUES(provider);

INSERT INTO stations (external_id, name, city, lat, lon, provider)
VALUES ('STN-003', 'East Station', 'Vayu Nagar', 18.5100, 73.9000, 'SYNTHETIC')
ON DUPLICATE KEY UPDATE name=VALUES(name), city=VALUES(city), lat=VALUES(lat),
    lon=VALUES(lon), provider=VALUES(provider);

INSERT INTO stations (external_id, name, city, lat, lon, provider)
VALUES ('STN-004', 'South Station', 'Vayu Nagar', 18.4900, 73.8600, 'SYNTHETIC')
ON DUPLICATE KEY UPDATE name=VALUES(name), city=VALUES(city), lat=VALUES(lat),
    lon=VALUES(lon), provider=VALUES(provider);

INSERT INTO documents (doc_key, title, publisher, version, url)
VALUES ('CPCB-AQI-2014',
        'Technical Document on Revised National Ambient Air Quality Standards',
        'CPCB (Central Pollution Control Board)', '2014', 'https://cpcb.nic.in/')
ON DUPLICATE KEY UPDATE title=VALUES(title), publisher=VALUES(publisher),
    version=VALUES(version), url=VALUES(url);

INSERT INTO documents (doc_key, title, publisher, version, url)
VALUES ('CPCB-GRADED-RESPONSE-2017',
        'Graded Response Action Plan (GRAP) for Delhi-NCR',
        'CPCB / Supreme Court Committee', '2017', 'https://cpcb.nic.in/')
ON DUPLICATE KEY UPDATE title=VALUES(title), publisher=VALUES(publisher),
    version=VALUES(version), url=VALUES(url);

INSERT INTO documents (doc_key, title, publisher, version, url)
VALUES ('WHO-AQG-2021',
        'WHO Global Air Quality Guidelines 2021',
        'World Health Organization', '2021',
        'https://www.who.int/publications/i/item/9789240034228')
ON DUPLICATE KEY UPDATE title=VALUES(title), publisher=VALUES(publisher),
    version=VALUES(version), url=VALUES(url);

INSERT INTO documents (doc_key, title, publisher, version, url)
VALUES ('AEROCAUSE-QC-GUIDE-V1',
        'AeroCause Internal QC Rule Reference (Heuristic)',
        'AeroCause Project Team', '0.1', NULL)
ON DUPLICATE KEY UPDATE title=VALUES(title), publisher=VALUES(publisher),
    version=VALUES(version), url=VALUES(url);
