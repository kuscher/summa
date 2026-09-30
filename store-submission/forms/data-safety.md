# Data safety (Play Console › Policy › App content › Data safety)

Google's definition: data is "collected" when it's transmitted off the device. Data processed only
on the device is not collected. Even apps that collect nothing must fill in this form and link a
privacy policy.

## Answers

1. **Does your app collect or share any of the required user data types?** No.
   - Sheets and settings stay on the device (app-private storage).
   - The only network requests download public exchange-rate tables (Frankfurter, ECB; CoinGecko
     only when the user turns on crypto prices). They carry no user data, identifiers or sheet
     content. Nothing is uploaded.
   - No analytics, crash reporting, ads or third-party SDKs that send data.
2. Because nothing is collected, the "security practices" questions (encryption in transit,
   deletion requests) don't apply. For the record: all rate downloads use HTTPS, and uninstalling
   deletes everything.
3. **Android backup:** sheets are included in the user's own Android device backup (Auto Backup).
   That's the Android system backing up to the user's Google account; the developer never receives
   it. Declare "No data collected" unless the form asks about system backup explicitly.

Resulting label: **No data collected. No data shared with third parties.**
