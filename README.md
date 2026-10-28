# Zegar, Budzik i Kalendarz

Kopia aplikacji „Zegar i Budzik” z **kalendarzem przypomnień w zegarze kuchennym**. Ma własny pakiet
`pl.kuchenny.zegar.kalendarz`, więc instaluje się obok starej aplikacji. Gotowy plik: `Zegar-Budzik-Kalendarz.apk`.

## Kalendarz w zegarze kuchennym

- Na ekranie tarczy przycisk **„Kalendarz”** → lista przypomnień i „Dodaj przypomnienie”.
- Wybierasz **dzień** (np. za miesiąc), **godzinę i minutę**, a potem **„Nagraj głos”** — co masz zrobić.
- O tej porze telefon odtwarza nagranie **jak dzwonek zegara**: tyle razy, ile „Powtórzenia” w ustawieniach,
  z tą samą głośnością i wibracją, najdłużej minutę — i **sam cichnie**. Niczego nie trzeba wyłączać.
- Po zagraniu przypomnienie i nagranie się kasują. Z listy można je wcześniej odsłuchać albo usunąć.
- Przypomnienia wracają po restarcie telefonu; jeśli telefon był wtedy wyłączony, zagra zaraz po włączeniu.
- **Głos dla timera**: na tarczy, obok „Zeruj”, mały przycisk 🎤. Ustawiasz czas, dotykasz 🎤, nagrywasz
  i „Zapisz i start”. Gdy czas minie, zamiast dzwonka zagra nagranie (powtórzenia z ustawień) i się skasuje.
  🎤✓ oznacza, że aktywny timer ma nagranie; „Usuń nagranie” wraca do zwykłego dzwonka.
- Kod: `core/Reminder.kt` (zapis listy), `alarm/Reminders.kt` (lista, alarmy, nagrania `notatki/<id>.m4a`),
  dzwoni ten sam `alarm/AlarmService` co timery (tryb `REMINDER`).

# Zegar i Budzik

Jedna natywna aplikacja na Androida (Kotlin): **kuchenny zegar** i **budzik** razem, pod jedną ikoną.
Na dole ekranu są dwie zakładki — „Zegar kuchenny” i „Budzik” — przełączane jednym dotknięciem.
Każda zakładka zachowuje swój stan; przycisk Wstecz chowa aplikację.

Powstała z dwóch osobnych aplikacji (moduły `app` i `budzik`); kod budzika jest teraz w module `app`
(pakiet `pl.kuchenny.budzik`), a kod zegara w pakiecie `pl.kuchenny.timer`. Ustawienia, powiadomienia
i alarmy obu części są od siebie niezależne.

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
```

- `appuild\outputspk\debugpp-debug.apk` — gotowa aplikacja (kopia: `Zegar-i-Budzik.apk`)
- Pakiet `pl.kuchenny.zegar`, więc instaluje się obok dwóch osobnych aplikacji.
- `ui/AppTabs.kt` — dolny pasek zakładek wspólny dla obu ekranów.

# Kuchenny zegar

## Ekrany

- **Tarcza** — pełny ekran, bez przewijania. Pośrodku tarczy widać, ile czasu zostało do zadzwonienia (albo ustawiony czas, gdy timer jeszcze nie odlicza). Palec przesuwa wyłącznie tarczę, wartość zmienia się od razu po dotknięciu.
- **Ustawienia** — pod przyciskiem „Ustawienia” w prawym górnym rogu: dzwonek, powtórzenia, siła wibracji, własny plik audio i zgody potrzebne, żeby alarm nie zasnął.

## Funkcje

- **Wiele timerów jednocześnie** — dwa na start, kolejne przyciskiem „+ Nowy”, do dziesięciu.
- **Własne nazwy timerów** — dotknij nazwy nad tarczą, aby ją zmienić albo usunąć timer.
- **Pasek timerów** u góry pokazuje wszystkie timery i przełącza między nimi; każdy odlicza niezależnie.
- Tryb **Godziny / Minuty** pod tarczą — decyduje, co ustawia przesuwanie palca.
- **Minuty jak pokrętło** — po pełnym obrocie czas idzie dalej: 0:59 przechodzi w 1:00, 1:59 w 2:00, aż do 23:59.
- **Szybkie dodawanie czasu**: +30 s, +1 min, +5 min — działa też w trakcie odliczania i w pauzie.
- Pod tarczą godzina zadzwonienia („GOTOWE O”) i aktualny czas („TERAZ”).
- Pauza, wznowienie i anulowanie timera; zmiana czasu w trakcie odliczania zaczyna nowe odliczanie.
- Przycisk **Zeruj** pod tarczą — natychmiast zatrzymuje timer i wraca na 0:00.
- Po zakończeniu dzwonienia timer sam wraca na 0:00, gotowy na następne ustawienie.
- **Osobne powiadomienie dla każdego działającego timera** z odliczaniem wstecz i przyciskiem zatrzymania.
- Osiem krótkich dzwonków kuchennych i serie 1–20 dzwonków; dzwonek gra do minuty albo do wybranej liczby powtórzeń.
- Własny plik audio wybierany z telefonu; dostęp do pliku jest zapamiętywany.
- Wibracja o regulowanej sile (0–10) i głośność dzwonka (suwak na ekranie tarczy).
- Gdy kilka timerów skończy się naraz, dzwonią po kolei — drugi alarm nie ginie.
- Alarm działa po schowaniu aplikacji i po restarcie telefonu.

## Jak to jest zbudowane

- `core/` — lista timerów i reguły przejść. `TimerState` ma cztery stany (`Idle`, `Running`, `Paused`, `Ringing`), a `TimerController` jest jedynym miejscem, które je zmienia. Warstwa nie zna Androida, więc ma testy jednostkowe.
- `alarm/` — planowanie alarmów (osobny `PendingIntent` na timer), serwis dzwonienia z kolejką, synteza dzwonka, odtwarzacz z wibracją i powiadomienia.
- `ui/` — strona z tarczą (`TimerPage`), strona ustawień (`SettingsScreen`) i dwa własne widoki (tarcza i pierścień postępu).
- `Settings.kt` — jedno miejsce z kluczami ustawień dzwonka.
- `TimerGraph.kt` — składa obiekty w jednym miejscu.

Stan timerów zapisujemy jako JSON w preferencjach (`TimerListCodec`), z migracją starego, pojedynczego timera.

## Budowanie i testy

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
```

## Historia wersji

### 2.5

- Po zakończeniu dzwonienia (i po jego zatrzymaniu) timer zeruje się do 0:00.
- Mały przycisk „Zeruj” obok przełącznika Godziny/Minuty.

### 2.4

- Przesuwanie minut nie zatrzymuje się na 59 — po pełnym obrocie dodaje się godzina.

### 2.3

- Pod linią „GOTOWE O” doszła aktualna godzina („TERAZ”).
- Usunięte ustawienie „Jak długo ma dzwonić”.
- W jego miejscu suwak siły wibracji (0 = wyłączona).
- Suwak głośności dzwonka na ekranie tarczy.

### 2.2

- Godzina „GOTOWE O” zmienia się już w trakcie przesuwania palcem.
- Wibracja: mocniejszy wzorzec z kategorią „alarm”.

### 2.1

- Tarcza na osobnym, pełnym ekranie — przesuwanie palcem nie przewija strony.
- Ustawienia dzwonka i zgód na drugim ekranie.
- Tryb Godziny / Minuty zamiast dotykania środka tarczy.

# Budzik

Druga aplikacja, niezależna od kuchennego zegara.

## Funkcje

- Wiele alarmów (do ośmiu), każdy z własną godziną, przełącznikiem i **dniami tygodnia** (Pn–Nd). Bez zaznaczonych dni alarm dzwoni raz i sam się wyłącza.
- Lista pokazuje, za ile zadzwoni najbliższy alarm.
- Dziesięć melodii (w tym ćwierkanie ptaków, kogut, kukułka i stary telefon) albo **własne dźwięki z telefonu** (mp3, m4a, ogg, wav, flac… — do dziesięciu), z przyciskiem „Odsłuchaj”. Gdy pliku zabraknie, budzik zagra wbudowaną melodię.
- Głośność końcowa i **narastanie głośności** ustawiane suwakiem od 0 do 2 minut (co 5 s).
- Wibracja o regulowanej sile.
- Wyłączanie ruchem: **potrząśnięcie** i **odwrócenie telefonu ekranem do dołu** (osobne przełączniki, siła potrząśnięcia ustawiana suwakiem od 1 do 10, z przyciskiem „Sprawdź potrząśnięcie”), a także przyciskiem na powiadomieniu.
- **Pykanie co godzinę** — przełącznik włącz/wyłącz. Jedno pyknięcie równo o pełnej godzinie (12:00, 13:00…), tylko w wybranym zakresie godzin (np. 8:00–22:00, może przechodzić przez północ), z własnym suwakiem głośności i przyciskiem „Odsłuchaj”. Pole „Mów, która jest godzina” zamienia pyknięcie na głos („Jest godzina ósma”, „Jest północ”) — nagrania `res/raw/godzina_00…23.ogg` zrobione F5-TTS (głos sklonowany z próbki) + RVC `polish_glos`. Gdy dzwoni budzik, pykanie milknie.
- Dzwoni do zatrzymania, w pętli, także po zgaszeniu ekranu.
- Alarmy wracają po restarcie telefonu i zmianie godziny.

## Jak to jest zbudowane

- `core/` — lista alarmów, wyliczanie następnego dzwonienia (`NextTrigger`) i narastanie głośności (`Ramp`); bez Androida, z testami.
- `alarm/` — planowanie alarmów systemowych, serwis dzwonienia z melodią i wibracją, detektor ruchu i powiadomienia.
- `MainActivity` — lista alarmów i ustawienia na jednej stronie.

## Ustawienia Androida

Dla najbardziej niezawodnego działania zezwól aplikacji na powiadomienia, dokładne alarmy i działanie bez optymalizacji baterii. Głośność alarmów musi być ustawiona powyżej zera.

Na telefonach takich jak Cubot system dość agresywnie usypia aplikacje w tle — warto dodać aplikację do wyjątków w ustawieniach baterii producenta. Android nadal kontroluje globalne wyciszenie głośności alarmu, tryb „Nie przeszkadzać” oraz wymuszone zatrzymanie aplikacji.
