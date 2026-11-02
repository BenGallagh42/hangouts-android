# Hangouts: an Android contacts and SMS app

A native Android app, written in Kotlin, for managing contacts and texting them. It works fully offline: contacts and conversations live in the app's own SQLite database, with no cloud backend and no third-party libraries.

## Features

- Create, edit, and delete contacts (first/last name, phone, email, address, photo)
- Contacts persisted locally in a custom SQLite table, not the system contacts provider
- Homepage list doubling as a message inbox: last-message preview, unread dot, newest-first ordering; tapping a row opens the conversation, tapping the avatar opens the profile
- **Real SMS**: send via `SmsManager`, receive via a `BroadcastReceiver` on `SMS_RECEIVED`, with a conversation history that distinguishes sender from receiver
- A message from an unrecognised number automatically creates a contact named after it
- Call a contact directly from the app
- Optional photo per contact, stored in app-private storage
- Menu to change the app's header color, persisted across sessions and screens
- English + French, resolved automatically from the system language (no in-app switcher)
- Toast showing how long the app was in the background, correct across process death and screen rotation
- Portrait and landscape support, including display-cutout handling
- Adaptive app icon with a legacy raster fallback

## Stack

- **Language:** Kotlin
- **UI:** native Android Views (XML layouts), no Jetpack Compose
- **Persistence:** SQLite via `SQLiteOpenHelper`, no ORM
- **Telephony:** `SmsManager` + a manifest-registered `BroadcastReceiver`, no third-party messaging layer
- **Dependencies:** only the AndroidX/Material baseline that ships by default with a new Android Studio project (appcompat, core-ktx, constraintlayout, material), and no third-party libraries
- **Min SDK:** 24 · **Target SDK:** 37
- **Permissions:** `SEND_SMS`, `RECEIVE_SMS`, `CALL_PHONE`, all requested at runtime, all with working fallbacks if denied

## Architecture

```
app/src/main/java/com/ft_hangouts/
├── model/
│   ├── Contact.kt              # data class: id, name, phone, email, address, photoUri
│   ├── Message.kt              # data class: id, contactId, body, timestamp, incoming, read
│   └── ContactSummary.kt       # a contact + their latest message + unread count (homepage row)
├── data/
│   ├── AppDbHelper.kt          # SQLiteOpenHelper singleton, owns both tables + all CRUD
│   └── PhotoStore.kt           # contact photos: copy to private storage, downsample, cache
├── MainActivity.kt             # homepage: inbox-style contact list, add/navigate entry points
├── AddEditContactActivity.kt   # single screen for both create and edit
├── ContactDetailActivity.kt    # contact fields + message/call/edit/delete entry points
├── ConversationActivity.kt     # message history + send via SmsManager
├── SmsReceiver.kt              # BroadcastReceiver for incoming SMS
├── BaseActivity.kt             # shared parent: header color, back arrow, edge-to-edge insets
├── ContactListAdapter.kt       # BaseAdapter for the homepage ListView
├── MessageListAdapter.kt       # BaseAdapter for the conversation ListView
├── HeaderColorManager.kt       # SharedPreferences-backed header color state
├── TimeFormat.kt               # locale-aware timestamps (time today / weekday / date)
└── HangoutsApplication.kt      # ActivityLifecycleCallbacks: app-wide background/foreground detection
```

### Database

Two tables in one SQLite database (`ft_hangouts.db`), managed by `AppDbHelper`:

- **`contacts`**: `id` (PK), `first_name`, `last_name`, `phone_number` (all `NOT NULL`), `email`, `address`, `photo_uri`
- **`messages`**: `id` (PK), `contact_id`, `body`, `timestamp`, `incoming` (0/1), `read` (0/1, drives the unread dot). Deleting a contact cascades to its messages.

`AppDbHelper` is a singleton (`getInstance(context)`, backed by `applicationContext`) shared across every screen, rather than one instance per Activity, which avoids leaking a database connection on every configuration change.

### Navigation

Four Activities connected via `Intent` extras (`EXTRA_CONTACT_ID`), no Navigation Component. From the homepage, tapping a row opens `ConversationActivity` and tapping the avatar opens `ContactDetailActivity`; the conversation's header carries an icon back to the profile, and the profile leads to `AddEditContactActivity`. Every screen below the homepage gets a back arrow from `BaseActivity`.

Screens reload their data in `onResume()` rather than `onCreate()`, so returning from an edit or a delete always reflects the current database state.

### Messaging

Messaging uses the real telephony stack rather than a simulation.

**Sending** goes through `SmsManager`, splitting anything over one SMS part with `divideMessage()` / `sendMultipartTextMessage()`. A send that the system rejects (no SIM, malformed number, no telephony hardware) is reported to the user instead of being stored as if it had gone out.

**Receiving** is handled by `SmsReceiver`, registered in the manifest for `android.provider.Telephony.SMS_RECEIVED`, so it runs even when the app isn't open. It reassembles multipart messages by sender, resolves the sender to a saved contact by comparing the last 7 digits (the network's format rarely matches what the user typed: `+66812345678` vs `0812345678`), stores the message, and broadcasts an internal event so any open screen refreshes immediately. A sender that matches no contact gets one created automatically, guarded by `PhoneNumberUtils.isGlobalPhoneNumber()` so alphanumeric sender IDs don't create junk entries.

Nothing in the receiver is allowed to throw. A crash in a manifest-registered receiver takes down the whole process, so a malformed message is dropped rather than fatal.

The app deliberately does **not** post its own notification for incoming messages: the phone's default SMS app already notifies, and duplicating it would show the user two notifications for one message.

To test incoming messages on an emulator: `adb emu sms send <number> "<text>"`.

### Photos

`PhotoStore` copies a picked image into app-private storage instead of persisting the picker's `content://` URI, whose read permission can expire and leave a permanently broken image. Images are downsampled on decode (a full-resolution camera photo decoded at native size throws `OutOfMemoryError`), EXIF orientation is applied so portrait shots aren't stored sideways, and list thumbnails are served from an `LruCache` so scrolling doesn't re-read and re-decode from disk on every row bind.

### Edge-to-edge handling

Every screen extends `BaseActivity`, which pads its content view using `WindowInsetsCompat` (status bar, navigation bar, display cutout, plus the action bar's own height) instead of relying on the framework to reserve that space automatically. Newer Android versions draw apps edge-to-edge by default, and without this, content renders underneath the header bar and system bars.

## Building

```
./gradlew assembleDebug
```

Or open the project directly in Android Studio and run it on an emulator or device.
