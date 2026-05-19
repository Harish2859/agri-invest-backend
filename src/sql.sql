-- 1. Users Table (Handles Investors, Farmers, and Village Leads)
CREATE TABLE users (
    id SERIAL PRIMARY KEY,
    full_name VARCHAR(100) NOT NULL,
    email VARCHAR(100) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(20) CHECK (role IN ('INVESTOR', 'FARMER', 'VILLAGE_LEAD')),
    aadhaar_no VARCHAR(12) UNIQUE,
    wallet_balance NUMERIC(12, 2) NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 2. Farm Projects Table (The "Micro-IPO")
-- 2. Farm Projects Table (The "Micro-IPO")
CREATE TABLE farm_projects (
    id SERIAL PRIMARY KEY,
    farmer_id INTEGER REFERENCES users(id),
    crop_type VARCHAR(50) NOT NULL,
    target_amount NUMERIC(12, 2) NOT NULL,
    equity_offered NUMERIC(5, 2), -- Just the data type here
    description TEXT,
    status VARCHAR(20) DEFAULT 'FUNDING', -- FUNDING, ACTIVE, COMPLETED, FAILED
    start_date DATE,
    end_date DATE
);

-- 3. Milestones Table (For the Village Lead to verify)
CREATE TABLE milestones (
    id SERIAL PRIMARY KEY,
    project_id INTEGER REFERENCES farm_projects(id),
    title VARCHAR(100), -- e.g., "Sowing", "Fertilizer", "Harvest"
    payout_percentage NUMERIC(5, 2), -- e.g., 20.00%
    is_verified BOOLEAN DEFAULT FALSE,
    verification_photo_url VARCHAR(255),
    verified_at TIMESTAMP
);

-- 4. Investments Table
CREATE TABLE investments (
    id SERIAL PRIMARY KEY,
    investor_id INTEGER REFERENCES users(id),
    project_id INTEGER REFERENCES farm_projects(id),
    amount_invested NUMERIC(12, 2) NOT NULL,
    investment_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
