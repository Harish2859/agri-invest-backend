ALTER TABLE farm_projects
ADD COLUMN IF NOT EXISTS withdrawable_balance DOUBLE PRECISION NOT NULL DEFAULT 0;

ALTER TABLE farm_projects
ADD COLUMN IF NOT EXISTS current_funding DOUBLE PRECISION NOT NULL DEFAULT 0;

ALTER TABLE farm_projects
ADD COLUMN IF NOT EXISTS final_farmer_profit DOUBLE PRECISION NOT NULL DEFAULT 0;

ALTER TABLE investments
ADD COLUMN IF NOT EXISTS final_return DOUBLE PRECISION;

ALTER TABLE investments
ADD COLUMN IF NOT EXISTS settled BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE users
ADD COLUMN IF NOT EXISTS kyc_document_url VARCHAR(255);

ALTER TABLE users
ADD COLUMN IF NOT EXISTS wallet_balance DOUBLE PRECISION NOT NULL DEFAULT 0;

ALTER TABLE users
ADD COLUMN IF NOT EXISTS kyc_status VARCHAR(32);

ALTER TABLE users
ADD COLUMN IF NOT EXISTS kyc_rejection_reason VARCHAR(255);

ALTER TABLE users
ADD COLUMN IF NOT EXISTS kyc_verified_at TIMESTAMP;

ALTER TABLE users
ALTER COLUMN wallet_balance TYPE NUMERIC(12,2) USING wallet_balance::numeric,
ALTER COLUMN wallet_balance SET DEFAULT 0.00;

ALTER TABLE farm_projects
ALTER COLUMN escrow_balance TYPE NUMERIC(12,2) USING escrow_balance::numeric,
ALTER COLUMN current_funding TYPE NUMERIC(12,2) USING current_funding::numeric,
ALTER COLUMN withdrawable_balance TYPE NUMERIC(12,2) USING withdrawable_balance::numeric,
ALTER COLUMN released_to_farmer TYPE NUMERIC(12,2) USING released_to_farmer::numeric,
ALTER COLUMN final_farmer_profit TYPE NUMERIC(12,2) USING final_farmer_profit::numeric,
ALTER COLUMN target_amount TYPE NUMERIC(12,2) USING target_amount::numeric;

ALTER TABLE withdrawal
ADD COLUMN IF NOT EXISTS user_id BIGINT;

UPDATE farm_projects
SET equity_offered = 0
WHERE equity_offered IS NULL;

ALTER TABLE farm_projects
ALTER COLUMN equity_offered SET DEFAULT 0;

-- KYC status backfill for legacy rows
UPDATE users
SET kyc_status = 'APPROVED'
WHERE kyc_status IS NULL AND verified = true;

UPDATE users
SET kyc_status = 'PENDING'
WHERE kyc_status IS NULL AND verified = false;

ALTER TABLE users
ALTER COLUMN kyc_status SET NOT NULL;

-- Fix ProjectStatus enum mapping (convert ordinal numbers to strings)
UPDATE farm_projects SET status = 'PENDING' WHERE status = '0';
UPDATE farm_projects SET status = 'FUNDING_IN_PROGRESS' WHERE status = '1';
UPDATE farm_projects SET status = 'FULLY_FUNDED' WHERE status = '2';
UPDATE farm_projects SET status = 'CROP_CYCLE_STARTED' WHERE status = '3';
UPDATE farm_projects SET status = 'COMPLETED' WHERE status = '4';
UPDATE farm_projects SET status = 'REJECTED' WHERE status = '5';


-- Fix KycStatus enum mapping
UPDATE users SET kyc_status = 'PENDING' WHERE kyc_status = '0';
UPDATE users SET kyc_status = 'SUBMITTED' WHERE kyc_status = '1';
UPDATE users SET kyc_status = 'APPROVED' WHERE kyc_status = '2';
UPDATE users SET kyc_status = 'REJECTED' WHERE kyc_status = '3';

