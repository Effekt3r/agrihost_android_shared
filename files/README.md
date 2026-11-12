# files

Where photos and PDFs live and how they reach Firebase Storage, the same in every assessor app
(hael is the reference; hael's own code moved here on 2026-11-12).

- **Photos:** `AgriPhotoCapture.newPhotoUri` makes a gallery row `Pictures/photo_{ms}.jpg` for the
  system camera; `onCaptured` records when/where (`PhotoCaptureLog`, prefs `photo_capture_log`) and
  queues a copy; `onCancelled` deletes the empty row. Apps keep the content URI as the link.
- **PDFs:** `AgriPdfs.dir` = `getExternalFilesDir(DOCUMENTS)`; `uri`/`view` via the app's
  `{applicationId}.fileprovider`. After a PDF is written call `AgriBackup.pdfCreated(context, file, PdfInfo)`.
- **Copies:** `AgriBackup.requestWifiPass` queues JobScheduler job 711302018 (unmetered network);
  `runForReport` copies one report now on any network (the app's Sync tap). Photos the app's
  `PhotoSource` links (unsent first), then photos the app took that nothing links, then pending PDFs.
  Paths: `{app}_photos/{client}/{report}/{land}_{landId}/test_{n}/photo{slot}_{taken}_{mediaId}.jpg`,
  `{app}_photos/{client}/_unlinked/{user}/...`, `{app}_pdfs/{client}/{report}/{file}.pdf`. Done-sets:
  prefs `photo_backup` (hael's name, so nothing is re-copied after the move) and `pdf_backup`
  (content hash ignoring iText's dates and /ID, so unchanged rebuilds are not re-sent).
  Each copy writes its console entry through `:audit` (`logPhoto` / `logPdf`).
- **DB exports:** `AgriDbExport.export / exportBackup / uploadPending` with the app's `DbExportSpec`
  (live DB, entry name, secret-scrubbing SQL, info text, extra files) ->
  `db_exports/{client}/{username}/{zip}` + a `db_exports` Firestore doc.

Init from `Application.onCreate` after `AgriAudit.init`:
`AgriFiles.init(app, FilesConfig(app = "mr", clientId = BuildConfig.FLAVOR + "_mr", ...))`.
Storage rules: `agrihost_hael_android/storage.rules` (PDF copies readable by console admins only).
