-- Voice notes: the recording (on Cloudinary) and its transcript, which becomes part of the note.
ALTER TABLE saves ADD COLUMN voice_url TEXT;
