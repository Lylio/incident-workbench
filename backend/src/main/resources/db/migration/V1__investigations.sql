CREATE TABLE incidents(id UUID PRIMARY KEY,created_at TIMESTAMP WITH TIME ZONE NOT NULL,title VARCHAR(120) NOT NULL,status VARCHAR(16) NOT NULL,report TEXT NOT NULL);
CREATE INDEX incident_date ON incidents(created_at DESC);
CREATE TABLE analyses(id UUID PRIMARY KEY,incident_id UUID NOT NULL REFERENCES incidents(id),created_at TIMESTAMP WITH TIME ZONE NOT NULL,report TEXT NOT NULL);
CREATE INDEX analysis_incident ON analyses(incident_id,created_at DESC);
CREATE TABLE reviews(id UUID PRIMARY KEY,incident_id UUID NOT NULL REFERENCES incidents(id),analysis_id UUID NOT NULL REFERENCES analyses(id),hypothesis_index INTEGER NOT NULL,status VARCHAR(16) NOT NULL,note TEXT NOT NULL,created_at TIMESTAMP WITH TIME ZONE NOT NULL);
