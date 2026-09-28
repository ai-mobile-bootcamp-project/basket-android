# Basket for Android

Offline-first grocery shopping lists: keep several lists, add items by hand or from the DummyJSON groceries catalog, tick them off aisle by aisle and always see what the trip costs. Lists live on the phone; only the catalog comes from the internet. English, Spanish and Arabic.

<table>
<tr><td align="center"><img src="docs/screens/00-welcome.png" width="180" alt="Welcome"></td><td align="center"><img src="docs/screens/01-lists.png" width="180" alt="Lists"></td><td align="center"><img src="docs/screens/02-list-detail.png" width="180" alt="List detail"></td><td align="center"><img src="docs/screens/03-add-edit-item.png" width="180" alt="Add / edit item"></td></tr>
<tr><td align="center">Welcome</td><td align="center">Lists</td><td align="center">List detail</td><td align="center">Add / edit item</td></tr>
<tr><td align="center"><img src="docs/screens/04-browse-products.png" width="180" alt="Browse products"></td><td align="center"><img src="docs/screens/05-product-detail.png" width="180" alt="Product detail"></td><td align="center"><img src="docs/screens/06-categories.png" width="180" alt="Categories"></td><td align="center"><img src="docs/screens/07-settings.png" width="180" alt="Settings"></td></tr>
<tr><td align="center">Browse products</td><td align="center">Product detail</td><td align="center">Categories</td><td align="center">Settings</td></tr>
</table>

## Run it

Android Studio Ladybug (2024.2)+ · JDK 17 · Android 8.0+ (minSdk 26).

```bash
./gradlew assembleDebug        # build
./gradlew test                 # unit tests (product rules)
./gradlew :app:lintDebug       # lint, as in CI
./gradlew assembleRelease      # minified release, signed with the debug key
```

## Project

- **Stack:** Kotlin 2.0 · Compose (Material 3) + XML views · Navigation (type-safe) · Hilt · Room · DataStore · Retrofit + OkHttp + kotlinx.serialization · Coil · AGP 8.7 / Gradle 8.11.
- **Modules:** `:app` (UI, data, navigation, theme) and `:core:domain` (pure Kotlin product rules in `BasketRules.kt`, with unit tests).
- **Screens:** Welcome, Lists, List detail and Browse products in Compose; Add / edit item, Product detail, Categories and Settings as XML Fragments in the same `NavHost`.
- **Data:** sample lists in `app/src/main/assets/sample-data.json`; catalog from `https://dummyjson.com/products/category/groceries` (no key); `core/domain/src/test/resources/groceries.json` is a saved response for tests only.
- **CI:** `.github/workflows/ci.yml` runs lint, unit tests and the release build on every push.

## Testing tips

| To test | Do this |
|---|---|
| Offline | `adb shell cmd connectivity airplane-mode enable` (and `disable`) |
| Spanish / Arabic | Settings → Language, or `adb shell cmd locale set-app-locales com.basket --locales es` |
| Dark theme | Settings → Theme → Dark |
| Largest font | `adb shell settings put system font_scale 2.0` (back to `1.0`) |
| Screen reader | Turn on TalkBack in the device settings |
| First launch again | `adb shell pm clear com.basket` |
| Catalog requests | Logcat tag `okhttp.OkHttpClient` (debug builds) |

## Tickets

31 bug reports and 5 feature requests. Tickets use the sample lists: tap **Get started** on first launch, or **Settings → Reset sample data**.

### Overview

| ID | Type | Screen | Ticket |
|---|---|---|---|
| [BK-101](#bk-101) | Bug | Lists | Card says "1 items without price" |
| [BK-102](#bk-102) | Bug | Lists | Card total doesn't match the list |
| [BK-103](#bk-103) | Bug | Lists | Progress bar is full at "3 of 10" |
| [BK-104](#bk-104) | Bug | Lists | Deleted a list by mistake and there's no Undo |
| [BK-105](#bk-105) | Bug | List detail | Ticked items stay in the middle of the list |
| [BK-106](#bk-106) | Bug | List detail | Swiping Milk removed a different item |
| [BK-107](#bk-107) | Bug | List detail | Undo after removing an item does nothing |
| [BK-108](#bk-108) | Bug | List detail | Sourdough bread shows $0.00 |
| [BK-109](#bk-109) | Bug | Browse → List detail | Apple is $1.74 in Browse but $1.73 on my list |
| [BK-110](#bk-110) | Bug | List detail | "Clear basket" deleted my whole list |
| [BK-111](#bk-111) | Bug | List detail | Aisles are in alphabetical order |
| [BK-112](#bk-112) | Bug | List detail | Sharing an empty list crashes the app |
| [BK-113](#bk-113) | Bug | Add / edit item | Editing an item makes a copy of it |
| [BK-114](#bk-114) | Bug | Add / edit item | Quantity can go to 0 and −1 |
| [BK-115](#bk-115) | Bug | Add / edit item | In Spanish, 1,99 becomes 199,00 |
| [BK-116](#bk-116) | Bug | Add / edit item | Back after saving opens the form again |
| [BK-117](#bk-117) | Bug | Add / edit item | Double-tapping Save adds the item twice |
| [BK-118](#bk-118) | Bug | Browse products | Browse shows a sofa, a bed and perfume |
| [BK-119](#bk-119) | Bug | Browse products | Searching "apple" shows iPhones |
| [BK-120](#bk-120) | Bug | Browse products | The app crashes without internet |
| [BK-121](#bk-121) | Bug | Browse products | Offline, Browse forgets the products it already loaded |
| [BK-122](#bk-122) | Bug | Browse products | Adding one Beef Steak adds 43 |
| [BK-123](#bk-123) | Bug | Browse products | Adding Apple again makes a second Apple row |
| [BK-124](#bk-124) | Bug | Browse products | Green Chili Pepper doesn't say "Low stock" |
| [BK-125](#bk-125) | Bug | Categories | Deleting a category deleted its items |
| [BK-126](#bk-126) | Bug | Settings | Dark theme turns off after reopening the app |
| [BK-127](#bk-127) | Bug | Whole app | Spanish is half English, and prices use "$" |
| [BK-128](#bk-128) | Bug | Whole app | Arabic isn't right-to-left |
| [BK-129](#bk-129) | Bug | Whole app | Dark mode has white rows and cards |
| [BK-130](#bk-130) | Bug | Whole app | Prices are cut off with large text |
| [BK-131](#bk-131) | Bug | Whole app | TalkBack says just "Button" |
| [BK-201](#bk-201) | Feature | New screen | Spending by aisle |
| [BK-202](#bk-202) | Feature | New button | Untick all |
| [BK-203](#bk-203) | Feature | New functionality | Quick add on List detail |
| [BK-204](#bk-204) | Feature | New text | Item count under the list name |
| [BK-205](#bk-205) | Feature | New button | Share a list from the Lists screen |

### Bug reports

<a id="bk-101"></a>

#### BK-101 · Card says "1 items without price"

| **Screen** | Lists |
|---|---|
| **Reported by** | QA |
| **Steps** | 1. Open **Lists**.<br>2. Look at the **Weekly shop** card, under the total. |
| **Expected** | "+ 1 without price". Every count is grammatical in English, Spanish and Arabic ("1 item", "2 items"). |
| **Actual** | "+ 1 items without price". |

<a id="bk-102"></a>

#### BK-102 · Card total doesn't match the list

| **Screen** | Lists |
|---|---|
| **Reported by** | App review, 2★ |
| **Steps** | 1. Open **Lists** and note the total on the **Weekly shop** card.<br>2. Open **Weekly shop** and look at the footer. |
| **Expected** | The card and the footer show the same total: **$45.84** for Weekly shop and **$102.91** for BBQ Saturday. |
| **Actual** | The card shows **$30.47** (BBQ Saturday **$33.58**) while the list says $45.84. |

<a id="bk-103"></a>

#### BK-103 · Progress bar is full at "3 of 10"

| **Screen** | Lists |
|---|---|
| **Reported by** | QA |
| **Steps** | 1. Open **Lists**.<br>2. Look at the **Weekly shop** card: "3 of 10 in basket". |
| **Expected** | The bar is 30% filled (3 of 10). |
| **Actual** | The bar is completely full. |

<a id="bk-104"></a>

#### BK-104 · Deleted a list by mistake and there's no Undo

| **Screen** | Lists |
|---|---|
| **Reported by** | Support |
| **Steps** | 1. Open **Lists**.<br>2. Tap ⋮ on **BBQ Saturday** → **Delete**. |
| **Expected** | The list disappears and the message "BBQ Saturday deleted" offers **Undo** for 5 seconds. Undo brings the list back exactly as it was (items, ticks, position). |
| **Actual** | The message appears without Undo. The list is gone for good. |

<a id="bk-105"></a>

#### BK-105 · Ticked items stay in the middle of the list

| **Screen** | List detail |
|---|---|
| **Reported by** | App review, 3★ |
| **Steps** | 1. Check that **Settings → Move ticked items down** is on (it is by default).<br>2. Open **Weekly shop** and tick **Apple**. |
| **Expected** | Apple moves into the **In basket** section at the bottom ("In basket · 4"). Unticking moves it back into its aisle. |
| **Actual** | Apple stays in Fruit & veg, struck through. There is no In basket section at all. |

<a id="bk-106"></a>

#### BK-106 · Swiping Milk removed a different item

| **Screen** | List detail |
|---|---|
| **Reported by** | Support |
| **Steps** | 1. Open **Weekly shop**.<br>2. Swipe **Milk** towards the start of the row. |
| **Expected** | Milk is removed and "Milk removed" offers Undo. |
| **Actual** | Another item disappears and Milk is still on the list. |

<a id="bk-107"></a>

#### BK-107 · Undo after removing an item does nothing

| **Screen** | List detail |
|---|---|
| **Reported by** | QA |
| **Steps** | 1. Open **Weekly shop**.<br>2. Remove any item by swiping it.<br>3. Tap **Undo** on the message straight away. |
| **Expected** | The item comes back in the same place, with the same quantity, price, note and tick. |
| **Actual** | Nothing happens. The item stays removed. |

<a id="bk-108"></a>

#### BK-108 · Sourdough bread shows $0.00

| **Screen** | List detail |
|---|---|
| **Reported by** | PO |
| **Steps** | 1. Open **Weekly shop**.<br>2. Look at **Sourdough bread** (it has no price) and at the footer. |
| **Expected** | The row shows "—" instead of a price, and the footer says "1 item without price" under the totals. |
| **Actual** | The row shows **$0.00** and the footer doesn't mention that an item has no price. |

<a id="bk-109"></a>

#### BK-109 · Apple is $1.74 in Browse but $1.73 on my list

| **Screen** | Browse → List detail |
|---|---|
| **Reported by** | App review, 2★ |
| **Steps** | 1. Open **Camping trip** → **Browse products**.<br>2. The Apple card shows **$1.74** (was $1.99). Tap **+** on Apple.<br>3. Go back to the list. |
| **Expected** | Apple's unit price on the list is **$1.74**, the same as the price shown in Browse. |
| **Actual** | The list shows Apple at **$1.73**, one cent less than Browse. |

<a id="bk-110"></a>

#### BK-110 · "Clear basket" deleted my whole list

| **Screen** | List detail |
|---|---|
| **Reported by** | App review, 1★ |
| **Steps** | 1. Open **Weekly shop** (3 items are ticked).<br>2. Tap ⋮ → **Clear basket**. |
| **Expected** | Only the 3 ticked items are removed, at once, and the message "3 items removed" offers **Undo** for 5 seconds. The 7 items still to buy stay. |
| **Actual** | All 10 items are removed and there is no Undo. |

<a id="bk-111"></a>

#### BK-111 · Aisles are in alphabetical order

| **Screen** | List detail |
|---|---|
| **Reported by** | PO |
| **Steps** | 1. Open **Weekly shop** and look at the aisle order.<br>2. Go to **Settings → Categories**, move **Drinks** to the top (drag, or ⋮ → Move up), and go back to the list. |
| **Expected** | Aisles follow the order on the Categories screen: Fruit & veg, Bakery, Dairy & eggs, … After moving Drinks to the top, Drinks comes first. |
| **Actual** | Aisles are alphabetical (Bakery, Dairy & eggs, Drinks, Fruit & veg, …) and reordering Categories changes nothing. |

<a id="bk-112"></a>

#### BK-112 · Sharing an empty list crashes the app

| **Screen** | List detail |
|---|---|
| **Reported by** | Crash report |
| **Steps** | 1. Open **Camping trip** (it has no items).<br>2. Tap **Share**. |
| **Expected** | Share is disabled on an empty list. The app never crashes. |
| **Actual** | The app closes. |

<a id="bk-113"></a>

#### BK-113 · Editing an item makes a copy of it

| **Screen** | Add / edit item |
|---|---|
| **Reported by** | Support |
| **Steps** | 1. Open **Weekly shop** and tap the **Apple** row.<br>2. Change the quantity from 6 to 7 and tap **Save changes**. |
| **Expected** | One Apple row with quantity 7. |
| **Actual** | Two rows: Apple × 6 and Apple × 7. |

<a id="bk-114"></a>

#### BK-114 · Quantity can go to 0 and −1

| **Screen** | Add / edit item |
|---|---|
| **Reported by** | QA |
| **Steps** | 1. Open any list → **Add item**.<br>2. Tap **−** on the quantity a few times. |
| **Expected** | The quantity stops at 1 and − is disabled there. + stops at 99. A typed quantity is kept between 1 and 99. |
| **Actual** | The quantity goes to 0, −1 and lower. |

<a id="bk-115"></a>

#### BK-115 · In Spanish, 1,99 becomes 199,00

| **Screen** | Add / edit item |
|---|---|
| **Reported by** | App review, 1★ (Spain) |
| **Steps** | 1. Go to **Settings → Language → Español**.<br>2. Open a list → **Añadir artículo**, name "Pan", unit price **1,99**, save. |
| **Expected** | The item costs **1,99 US$**. |
| **Actual** | The item costs **199,00**. |

<a id="bk-116"></a>

#### BK-116 · Back after saving opens the form again

| **Screen** | Add / edit item |
|---|---|
| **Reported by** | QA |
| **Steps** | 1. Open **Weekly shop** → **Add item**, name "Oat milk", tap **Add to Weekly shop**.<br>2. Press **Back**. |
| **Expected** | After saving you're on Weekly shop, and Back goes to **Lists**. The form is never shown again. |
| **Actual** | Back shows the Add item form again. |

<a id="bk-117"></a>

#### BK-117 · Double-tapping Save adds the item twice

| **Screen** | Add / edit item |
|---|---|
| **Reported by** | Support |
| **Steps** | 1. Open **Weekly shop** → **Add item**, name "Oat milk".<br>2. Double-tap **Add to Weekly shop** quickly. |
| **Expected** | One "Oat milk" row. |
| **Actual** | Two "Oat milk" rows. |

<a id="bk-118"></a>

#### BK-118 · Browse shows a sofa, a bed and perfume

| **Screen** | Browse products |
|---|---|
| **Reported by** | App review, 1★ |
| **Steps** | 1. Open any list → **Browse products**. |
| **Expected** | Only the grocery catalog: **27 products** (Apple, Beef Steak, Cat Food, …). |
| **Actual** | "30 products", including furniture, perfume and make-up. |

<a id="bk-119"></a>

#### BK-119 · Searching "apple" shows iPhones

| **Screen** | Browse products |
|---|---|
| **Reported by** | App review, 2★ |
| **Steps** | 1. Open **Browse products**.<br>2. Search for **apple**. |
| **Expected** | Only groceries that match: **Apple**. Search is instant, works offline, ignores case, and combines with the aisle chips. |
| **Actual** | Results include Apple AirPods, iPhones and MacBooks. |

<a id="bk-120"></a>

#### BK-120 · The app crashes without internet

| **Screen** | Browse products |
|---|---|
| **Reported by** | Crash report |
| **Steps** | 1. Start fresh: clear the app's data (`adb shell pm clear com.basket`), then tap **Get started**.<br>2. Turn on airplane mode (emulator: `adb shell cmd connectivity airplane-mode enable`).<br>3. Open **Weekly shop** → **Browse products**. |
| **Expected** | The "Can't load products" screen with "Check your connection and try again." and **Retry**. The rest of the app keeps working. |
| **Actual** | The app closes. |

<a id="bk-121"></a>

#### BK-121 · Offline, Browse forgets the products it already loaded

| **Screen** | Browse products |
|---|---|
| **Reported by** | Support |
| **Steps** | 1. Online, open **Browse products** and wait for the products.<br>2. Close the app from Recents and open it again.<br>3. Turn on airplane mode (emulator: `adb shell cmd connectivity airplane-mode enable`).<br>4. Open **Browse products** again. |
| **Expected** | The products saved earlier are shown, with the banner "You're offline · showing products saved on <date>" and Retry. |
| **Actual** | "Can't load products" (or the app closes, see BK-120). Nothing that was loaded earlier is kept. |

<a id="bk-122"></a>

#### BK-122 · Adding one Beef Steak adds 43

| **Screen** | Browse products |
|---|---|
| **Reported by** | App review, 1★ |
| **Steps** | 1. Open **Camping trip** → **Browse products**.<br>2. Tap **+** on **Beef Steak** once, then go back to the list. |
| **Expected** | Beef Steak × **1**. |
| **Actual** | Beef Steak × **43**. |

<a id="bk-123"></a>

#### BK-123 · Adding Apple again makes a second Apple row

| **Screen** | Browse products |
|---|---|
| **Reported by** | QA |
| **Steps** | 1. Open **Weekly shop** → **Browse products**. The Apple card shows the stepper "− 6 +".<br>2. Tap **+** on Apple and go back to the list. |
| **Expected** | Still one Apple row, now × 7. The stepper on the card shows 7. |
| **Actual** | A second Apple row appears. |

<a id="bk-124"></a>

#### BK-124 · Green Chili Pepper doesn't say "Low stock"

| **Screen** | Browse products |
|---|---|
| **Reported by** | PO |
| **Steps** | 1. Open **Browse products** and find **Green Chili Pepper**.<br>2. Open its detail. |
| **Expected** | The card shows a **Low stock** badge (icon and text), like the detail screen does. |
| **Actual** | No badge on the card. Only the detail screen says Low stock. |

<a id="bk-125"></a>

#### BK-125 · Deleting a category deleted its items

| **Screen** | Categories |
|---|---|
| **Reported by** | Support |
| **Steps** | 1. Go to **Settings → Categories**.<br>2. Tap ⋮ on **Dairy & eggs** → **Delete**.<br>3. Open **Weekly shop**. |
| **Expected** | First a dialog: "Delete Dairy & eggs?" · "Its 2 items move to Other." · Cancel / Delete. After Delete, Milk and Eggs are in **Other**. |
| **Actual** | No dialog. Milk and Eggs are gone from Weekly shop (10 items become 8). |

<a id="bk-126"></a>

#### BK-126 · Dark theme turns off after reopening the app

| **Screen** | Settings |
|---|---|
| **Reported by** | App review, 3★ |
| **Steps** | 1. Go to **Settings → Theme → Dark**.<br>2. Close the app from Recents and open it again. |
| **Expected** | The app is still dark, and Settings still says Dark. |
| **Actual** | The app is back to the system theme. |

<a id="bk-127"></a>

#### BK-127 · Spanish is half English, and prices use "$"

| **Screen** | Whole app |
|---|---|
| **Reported by** | App review, 2★ (Spain) |
| **Steps** | 1. Go to **Settings → Language → Español**.<br>2. Open **Weekly shop**, **Browse products** and **Añadir artículo**. |
| **Expected** | Everything is in Spanish except product names from the catalog. Prices use the Spanish format: **45,84 US$**. |
| **Actual** | Some labels stay in English, and some prices show as "$45.84" or "$1,99". |

<a id="bk-128"></a>

#### BK-128 · Arabic isn't right-to-left

| **Screen** | Whole app |
|---|---|
| **Reported by** | App review, 2★ |
| **Steps** | 1. Go to **Settings → Language → العربية**.<br>2. Open **Lists**, **Weekly shop** and **Add item**. |
| **Expected** | The layout is mirrored: the back arrow points right, checkboxes are on the right, and totals on the left. Prices still read left to right ("$10.44"), and − still decreases. |
| **Actual** | Everything stays left-to-right. |

<a id="bk-129"></a>

#### BK-129 · Dark mode has white rows and cards

| **Screen** | Whole app |
|---|---|
| **Reported by** | App review, 3★ |
| **Steps** | 1. Go to **Settings → Theme → Dark**.<br>2. Open **Weekly shop** and **Browse products**. |
| **Expected** | Every background is dark. Nothing stays white. |
| **Actual** | Item rows and product cards are white. |

<a id="bk-130"></a>

#### BK-130 · Prices are cut off with large text

| **Screen** | Whole app |
|---|---|
| **Reported by** | Accessibility audit |
| **Steps** | 1. Set the largest font size (Settings → Display → Font size, or `adb shell settings put system font_scale 2.0`).<br>2. Open **Weekly shop** and **Browse products**. |
| **Expected** | Rows and product cards grow taller and prices wrap. Nothing is cut off or overlaps. |
| **Actual** | Prices and names are clipped in item rows and product cards. |

<a id="bk-131"></a>

#### BK-131 · TalkBack says just "Button"

| **Screen** | Whole app |
|---|---|
| **Reported by** | Accessibility audit |
| **Steps** | 1. Turn on TalkBack.<br>2. On **Weekly shop**, focus **Share**. On **Add item**, focus **−** and **+**. On **Browse products**, focus **+** on a card. On **Edit item**, focus **Delete**. |
| **Expected** | Each button has a spoken label: "Share list", "Decrease quantity", "Increase quantity", "Add Apple to list", "Delete item". |
| **Actual** | TalkBack reads only "Button" (or "Unlabelled, button"). |

### Feature requests

<a id="bk-201"></a>

#### BK-201 · Spending by aisle

| **Type** | New screen |
|---|---|
| **Requested by** | PO |

**Why:** shoppers want to see where the money goes before they leave home.

**Where:** List detail → ⋮ → **Spending by aisle** opens a new screen. Back returns to List detail.

**Acceptance criteria**
- Top bar: back arrow, title **Spending by aisle**, subtitle with the list name.
- One row per aisle that has items (ticked and unticked), in the order of the Categories screen. Empty aisles are hidden.
- Each row: the category emoji (default categories only, hidden from the screen reader), category name, item count ("1 item", "4 items") and the aisle total at the end. An aisle with items without a price shows "+ 1 without price" under its total. An aisle where no item has a price shows "—" as its total.
- Footer: **Total**, which is always equal to the List detail total.
- An empty list shows "Nothing on this list yet".
- Light and dark theme; English, Spanish and Arabic (mirrored); works at the largest text size.

**Expected for Weekly shop**

| Aisle | Items | Total |
|---|---|---|
| 🍎 Fruit & veg | 4 items | $21.71 |
| 🍞 Bakery | 1 item | — (+ 1 without price) |
| 🥛 Dairy & eggs | 2 items | $8.68 |
| 🫙 Pantry | 1 item | $5.43 |
| 🧃 Drinks | 1 item | $7.86 |
| 🧻 Household & pets | 1 item | $2.16 |
| **Total** | | **$45.84** |

**Strings**

| Key | English | Spanish | Arabic |
|---|---|---|---|
| Menu item and title | Spending by aisle | Gasto por pasillo | الإنفاق حسب الممر |

<a id="bk-202"></a>

#### BK-202 · Untick all

| **Type** | New button |
|---|---|
| **Requested by** | PO |

**Why:** people reuse the same weekly list; after a trip they want every item back on the "to buy" side in one tap.

**Where:** List detail → ⋮ → **Untick all**, below Clear basket.

**Acceptance criteria**
- Disabled when nothing on the list is ticked.
- Unticks every item at once; the items go back into their aisles, and the Lists card shows "0 of 10 in basket".
- A message "3 items unticked" offers **Undo** for 5 seconds, which ticks exactly the same items again.
- The count is grammatical in every language ("1 item unticked").

**Strings**

| Key | English | Spanish | Arabic |
|---|---|---|---|
| Menu item | Untick all | Desmarcar todo | إلغاء تحديد الكل |
| Message (3) | 3 items unticked | 3 artículos desmarcados | تم إلغاء تحديد 3 عناصر |

<a id="bk-203"></a>

#### BK-203 · Quick add on List detail

| **Type** | New functionality |
|---|---|
| **Requested by** | PO |

**Why:** adding an item through the full form takes too long in the store.

**Where:** a text field at the top of List detail, above the first aisle, with a **+** button at the end (spoken label "Add item").

**Acceptance criteria**
- Typing a name and pressing Enter/Done (or +) adds it to the list with quantity 1. The field clears and keeps focus for the next item.
- If the name matches a catalog product (ignoring case and outer spaces) from the products saved on the phone, the item gets that product's price you pay, its category and the catalog icon. Otherwise it has no price and goes to **Other**.
- If the item is already on the list (same name ignoring case and outer spaces), its quantity goes up by 1 instead of adding a second row (maximum 99).
- A message "Kiwi added" offers **Undo** for 5 seconds.
- Empty or spaces-only input does nothing; the field accepts at most 60 characters.
- Works offline: without saved products, everything is added as a typed item.

**Examples on Weekly shop**
- "kiwi" → Kiwi, $2.11, Fruit & veg, catalog icon.
- "Candles" → Candles, no price ("—"), Other.
- "milk" → no new row; Milk becomes × 3.

**Strings**

| Key | English | Spanish | Arabic |
|---|---|---|---|
| Placeholder | Add an item | Añadir un artículo | أضف عنصرًا |
| Message | Kiwi added | Kiwi añadido | تمت إضافة Kiwi |

<a id="bk-204"></a>

#### BK-204 · Item count under the list name

| **Type** | New text |
|---|---|
| **Requested by** | PO |

**Why:** in the store people want to know at a glance how much is left.

**Where:** List detail top bar, under the list name.

**Acceptance criteria**
- Shows "10 items · 3 in basket". With nothing ticked: "6 items". An empty list: "No items yet".
- Updates immediately when items are ticked, added or removed.
- Counts are grammatical in every language ("1 item").
- One line; the list name and the subtitle each end with "…" when too long. Works at the largest text size.

**Strings**

| Case | English | Spanish | Arabic |
|---|---|---|---|
| Weekly shop | 10 items · 3 in basket | 10 artículos · 3 en la cesta | 10 عناصر · 3 في السلة |
| BBQ Saturday | 6 items | 6 artículos | 6 عناصر |
| Camping trip | No items yet | Sin artículos todavía | لا توجد عناصر بعد |

<a id="bk-205"></a>

#### BK-205 · Share a list from the Lists screen

| **Type** | New button |
|---|---|
| **Requested by** | PO |

**Why:** people share the shopping list with a partner without opening it.

**Where:** the ⋮ menu on each list card on **Lists**: Rename, Duplicate, **Share**, Delete.

**Acceptance criteria**
- Opens the system share sheet with exactly the same text as **Share** on List detail (list name, "To buy", "In basket", total).
- Not available for a list with no items (disabled or hidden), and it never crashes.
- Spoken label "Share list".

**Strings**

| Key | English | Spanish | Arabic |
|---|---|---|---|
| Menu item | Share | Compartir | مشاركة |

## Version

1.0.0. See [CHANGELOG.md](CHANGELOG.md).
