# Multiple Tags per Video & Project Description Upgrade

This plan outlines the structural database changes and application updates required to support choosing multiple interests per project and adding a dedicated deep-dive "Deskripsi Lengkap" column.

## User Review Required
> [!IMPORTANT]
> This upgrade requires a **schema migration** on Supabase. Since a post can now have multiple tags, the `interest_id` column currently on the `posts` table must be migrated to a new nested table called `post_interests`, and a new `project_description` column must be added to `posts`.
> Below is the SQL logic that you will need to run on your Supabase dashboard once we are ready.

```sql
-- 1. Tambah kolom deskripsi pada posts
ALTER TABLE public.posts ADD COLUMN project_description TEXT NULL;

-- 2. Hapus referensi interest tunggal sebelumnya (opsional, ditaruh sini amannya)
-- ALTER TABLE public.posts DROP COLUMN interest_id;

-- 3. Buat tabel jembatan (Junction Table) untuk N:M relationship tag video
CREATE TABLE public.post_interests (
    id BIGSERIAL PRIMARY KEY,
    post_id BIGINT NOT NULL,
    interest_id BIGINT NOT NULL,
    CONSTRAINT post_interests_post_id_fkey FOREIGN KEY (post_id) REFERENCES public.posts (id) ON DELETE CASCADE,
    CONSTRAINT post_interests_interest_id_fkey FOREIGN KEY (interest_id) REFERENCES public.interest (id) ON DELETE CASCADE,
    CONSTRAINT unique_post_interest UNIQUE (post_id, interest_id)
) TABLESPACE pg_default;

-- RLS Policy (Read All, Insert if Owner)
ALTER TABLE public.post_interests ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Enable read operations for all" ON public.post_interests FOR SELECT USING (true);
CREATE POLICY "Enable insert for authenticated users only" ON public.post_interests FOR INSERT WITH CHECK (auth.uid() IN (SELECT user_id FROM public.posts WHERE id = post_id));
```

## Proposed Changes

### Database Layer (Models)
#### [MODIFY] [Model.kt](file:///c:/Users/Makima/AndroidStudioProjects/DispatchApp/app/src/main/java/com/example/dispatchapp/models/Model.kt)
- Create new data class `PostInterest` that maps the joined response.
- Add `val post_interests: List<PostInterest>? = null` to `Post` data class.
- Add `val projectDescription: String? = null` to `Post` data class.

---

### UI Core Layer
#### [MODIFY] [activity_create_post.xml](file:///c:/Users/Makima/AndroidStudioProjects/DispatchApp/app/src/main/res/layout/activity_create_post.xml)
- Change `<AutoCompleteTextView>` for Interest into a `<com.google.android.material.chip.ChipGroup>` or multiselect dropdown wrapper that allows appending multiple Chips.
- Add a new `<TextInputEditText>` named `etDescription` for typing the rich text description.

#### [MODIFY] [CreatePostActivity.kt](file:///c:/Users/Makima/AndroidStudioProjects/DispatchApp/app/src/main/java/com/example/dispatchapp/CreatePostActivity.kt)
- Refactor the selection logic to gather a `List<Long>` of selected Interest IDs.
- On `uploadPost()`, insert the post first, read back its generated `id` `(.select().decodeSingle<Post>())`, and perform a batch bulk-insert to `post_interests` with the array of selected tags.

---

### Home/Showcase Reading Layer
#### [MODIFY] [ShowcaseFragment.kt](file:///c:/Users/Makima/AndroidStudioProjects/DispatchApp/app/src/main/java/com/example/dispatchapp/fragments/ShowcaseFragment.kt)
- Refactor the Supabase query: `columns = Columns.raw("*, ... post_interests(interest(id, interest))")`
- Same applies to `SearchReelsActivity.kt` querying nested schemas.

#### [MODIFY] [ShowcaseReelAdapter.kt](file:///c:/Users/Makima/AndroidStudioProjects/DispatchApp/app/src/main/java/com/example/dispatchapp/adapters/ShowcaseReelAdapter.kt)
- Update `<TextView>` bindings to check `.post_interests`. If array size > 1, take index `[0]` name and append `"+" + (size - 1)`. 
- Bind `project_description` into the expanded text block conditionally if it exists.

## Open Questions
- Is there any specific layout behavior you want for "Deskripsi lengkap"? By default, I will attach it right under the short `caption` when the user clicks **"Lihat Selengkapnya"**. Does that sound good?
