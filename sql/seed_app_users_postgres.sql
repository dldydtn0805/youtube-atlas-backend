BEGIN;

INSERT INTO app_users (
    google_subject,
    email,
    display_name,
    picture_url,
    created_at,
    last_login_at
)
VALUES
    ('google-subject-1001', 'atlas.alpha@example.com', 'Atlas Alpha', 'https://i.pravatar.cc/300?img=1', '2026-04-01T09:00:00Z', '2026-04-04T08:30:00Z'),
    ('google-subject-1002', 'atlas.bravo@example.com', 'Atlas Bravo', 'https://i.pravatar.cc/300?img=2', '2026-04-01T09:10:00Z', '2026-04-04T08:35:00Z'),
    ('google-subject-1003', 'atlas.charlie@example.com', 'Atlas Charlie', 'https://i.pravatar.cc/300?img=3', '2026-04-01T09:20:00Z', '2026-04-04T08:40:00Z'),
    ('google-subject-1004', 'atlas.delta@example.com', 'Atlas Delta', 'https://i.pravatar.cc/300?img=4', '2026-04-01T09:30:00Z', '2026-04-04T08:45:00Z'),
    ('google-subject-1005', 'atlas.echo@example.com', 'Atlas Echo', 'https://i.pravatar.cc/300?img=5', '2026-04-01T09:40:00Z', '2026-04-04T08:50:00Z'),
    ('google-subject-1006', 'atlas.foxtrot@example.com', 'Atlas Foxtrot', 'https://i.pravatar.cc/300?img=6', '2026-04-01T09:50:00Z', '2026-04-04T08:55:00Z'),
    ('google-subject-1007', 'atlas.golf@example.com', 'Atlas Golf', 'https://i.pravatar.cc/300?img=7', '2026-04-01T10:00:00Z', '2026-04-04T09:00:00Z'),
    ('google-subject-1008', 'atlas.hotel@example.com', 'Atlas Hotel', 'https://i.pravatar.cc/300?img=8', '2026-04-01T10:10:00Z', '2026-04-04T09:05:00Z'),
    ('google-subject-1009', 'atlas.india@example.com', 'Atlas India', 'https://i.pravatar.cc/300?img=9', '2026-04-01T10:20:00Z', '2026-04-04T09:10:00Z'),
    ('google-subject-1010', 'atlas.juliet@example.com', 'Atlas Juliet', 'https://i.pravatar.cc/300?img=10', '2026-04-01T10:30:00Z', '2026-04-04T09:15:00Z')
ON CONFLICT (email) DO NOTHING;

COMMIT;
