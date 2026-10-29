-- 16시즌 소환사의 협곡 구매 가능 신발 (Data Dragon 16.21.1 ko_KR). boot_purchases 가 마지막 구매 신발을 고르므로 1단계 장화도 포함한다.
INSERT INTO item_meta (season, item_id, type, item_name, active) VALUES
    (16, 1001, 'BOOTS', '장화', true),
    (16, 3006, 'BOOTS', '광전사의 군화', true),
    (16, 3008, 'BOOTS', '탐욕의 군화', true),
    (16, 3009, 'BOOTS', '신속의 장화', true),
    (16, 3020, 'BOOTS', '마법사의 신발', true),
    (16, 3047, 'BOOTS', '판금 장화', true),
    (16, 3111, 'BOOTS', '헤르메스의 발걸음', true),
    (16, 3158, 'BOOTS', '명석함의 아이오니아 장화', true),
    (16, 3168, 'BOOTS', '불멸의 길', true),
    (16, 3170, 'BOOTS', '신속행진', true),
    (16, 3171, 'BOOTS', '핏빛 명석함', true),
    (16, 3173, 'BOOTS', '사슬끈 분쇄자', true),
    (16, 3174, 'BOOTS', '무장 진격', true),
    (16, 3175, 'BOOTS', '주문투척자의 신발', true)
ON CONFLICT (season, item_id) DO NOTHING;
