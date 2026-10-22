-- The replay's mission time at the moment of a review, next to the wall-clock created_at,
-- so the UI can show both (review "at" is real time; the demo's timeline is mission time).
ALTER TABLE review_action ADD COLUMN mission_time TIMESTAMP WITH TIME ZONE;
