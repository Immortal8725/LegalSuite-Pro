# Firm-branded document templates

Every firm on Owlaw generates documents on its own letterhead. The same template comes out with each firm's logo, firm name, address, phone, email, reference, attorney and footer.

## Where things live

- **Letterhead:** Settings, then "Letterhead for generated documents". Owners and admins can upload a logo (PNG or JPEG, up to 2 MB) and set the firm details, reference prefix, POPIA information officer, trust banking details, letterhead colour and footer text. If the footer is left blank, each attorney's documents use their own name and title from their profile, for example "Thabo Ndlovu, Director".
- **Generate:** open a matter, go to Files and choose Generate document. Pick a template, check the details Owlaw has filled in, complete any blanks, preview the PDF, then save PDF and Word copies to the matter. You can also send the document for e-signature or email it. The mobile app has the same action on the matter screen.
- **Library:** the Templates page lists the system templates and the firm's own Word templates. You can also upload new ones there.

## System templates

These are the firm-supplied originals. The wording is unchanged, and only the placeholders became fields.

| Template | Source | Pages |
|---|---|---|
| RAF claim pack: Special Power of Attorney and authorisations | PDF | 15 |
| New practice announcement | PDF | 1 |
| Letter to the Master: reporting a deceased estate | PDF | 2 |
| Client mandate and fee agreement (with Annexure B fee schedule) | PDF, pages 1 to 10 | 10 |
| Privacy policy (POPIA) | PDF, pages 11 to 16 | 6 |
| Client information sheet: individual | PDF, pages 17 to 18 | 2 |
| Client information sheet: company, CC or trust | PDF, pages 19 to 20 | 2 |
| Notice of termination of mandate (deceased estate) | DOCX | |
| General power of attorney (estate of the late) | DOCX | |

PDF originals are stored as positioned layouts (text, lines, boxes and images where they were), so the output matches the original page for page. DOCX originals stay as Word files. `scripts/templates/convert.py` rebuilds them from the source files.

## Fields

Write a field as `{{name}}`. Owlaw fills firm and attorney fields from the letterhead and the signed-in user, and it pre-fills matter fields from the chosen matter. Any other name becomes a box to complete when the document is generated. `{{name:____}}` prints the text after the colon (for example the original blank line) if the field is left empty. Blanks left empty therefore still print as lines, so the document can be completed by hand.

| Field | Filled from | Meaning |
|---|---|---|
| `{{firm_logo}}` | Letterhead | Uploaded logo, placed where the field sits. Without a logo, the firm name prints in the letterhead colour. |
| `{{firm_name}}` | Letterhead | Registered firm name |
| `{{firm_address}}` | Letterhead | Street, city, province and postal code |
| `{{firm_phone}}` | Letterhead | Office telephone |
| `{{firm_email}}` | Letterhead | Office email |
| `{{firm_ref}}` | Letterhead | Reference prefix for "Our Ref", or the attorney's initials if none is set |
| `{{firm_website}}` | Letterhead | Website |
| `{{information_officer}}` | Letterhead | POPIA information officer |
| `{{trust_account_name}}` | Letterhead | Trust account holder |
| `{{trust_account_number}}` | Letterhead | Trust account number |
| `{{bank_name}}` | Letterhead | Bank |
| `{{branch_name}}` | Letterhead | Branch name |
| `{{branch_code}}` | Letterhead | Branch code |
| `{{footer_text}}` | Letterhead | Footer line. Defaults to `attorney_name, attorney_title`. |
| `{{attorney_name}}` | Profile | Signed-in attorney's full name |
| `{{attorney_title}}` | Profile | Title or qualification |
| `{{attorney_initials}}` | Profile | Initials |
| `{{attorney_email}}` | Profile | Email |
| `{{date}}` | Today | For example 15 October 2026 |
| `{{client_name}}` | Matter | Client on the matter |
| `{{client_id_number}}` | Matter | Remembered on the matter once entered |
| `{{client_email}}`, `{{client_phone}}`, `{{client_address}}` | Matter | Client contact details |
| `{{matter_ref}}`, `{{file_number}}` | Matter | Matter number |
| `{{matter_title}}`, `{{court_name}}`, `{{court_case_number}}`, `{{opposing_party}}` | Matter | Matter details |
| `{{accident_date}}` | Matter | RAF matters: the accrual date on the matter |
| `{{deceased_name}}`, `{{date_of_death}}`, `{{estate_number}}` | Matter | Remembered on the matter once entered |

Fill-in fields in the system templates include `signed_at`, `signed_day`, `signed_month`, `signed_year`, `letter_day`, `letter_month`, `witness_1`, `witness_2`, `previous_attorney`, `hospital_name`, `saps_ar_number`, `represented_name`, `first_heir_name`, `recipient_name` and more. Values typed for a matter (other than dates) are remembered and pre-filled the next time.

## Uploading your own Word template

1. Write the document in Word with your fields, for example `Dear {{client_name}}` or `Our Ref: {{firm_ref}}/{{file_number}}`.
2. Put `{{firm_logo}}` where the logo should go. If you leave it out, Owlaw adds a letterhead (logo, firm name and contact line) in the header. If there is no footer, it adds `{{footer_text}}`.
3. `[BRACKET PROMPTS]` such as `[AMOUNT]` become fill-in fields, and they print the prompt until filled.
4. Upload it on the Templates page. Only `.docx` is accepted (save older `.doc` files as `.docx` first), up to 5 MB.

Word output keeps your formatting exactly. The PDF version of an uploaded Word template is laid out by Owlaw (letterhead, paragraphs, list numbering, simple tables, footer and page numbers). Complex Word layouts such as text boxes, columns and floating images are simplified in the PDF, so use the Word copy where layout matters.

## API

| Method and path | Purpose |
|---|---|
| `GET /api/v1/firm/branding`, `PUT /api/v1/firm/branding` | Read or update the letterhead (update is for admins only) |
| `POST /api/v1/firm/branding/logo` (multipart `file`), `GET /api/v1/firm/branding/logo` | Upload or fetch the firm's own logo |
| `GET /api/v1/doc-templates` | System templates and the firm's templates |
| `GET /api/v1/doc-templates/fields` | Field reference |
| `GET /api/v1/doc-templates/{id}/form?caseId=` | Fields with known values filled in |
| `POST /api/v1/doc-templates/{id}/preview` | PDF preview (`caseId`, `values`) |
| `POST /api/v1/doc-templates/{id}/generate` | Save to the matter (`caseId`, `values`, `formats` of pdf and/or docx, optional `sign` with signer name and email, optional `email` with `to`) |
| `POST /api/v1/doc-templates/upload` (multipart `file`, `name`, `category`) | Add a firm Word template |
| `DELETE /api/v1/doc-templates/{id}` | Remove a firm template (admins only) |

Each firm sees only its own templates, logo and generated files.
