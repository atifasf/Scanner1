# How to manually generate the AAB file

Since the AI Studio publisher is currently experiencing a rate limit (HTTP 429 Error), you can generate your `.aab` file manually using Android Studio.

1. **Download the ZIP**: Click the gear icon in AI Studio and select **Export** to download your project as a ZIP file.
2. **Extract the ZIP**: Unzip the downloaded file on your computer.
3. **Open in Android Studio**: Open Android Studio, click **Open**, and select the extracted folder.
4. **Wait for Sync**: Allow Android Studio a few minutes to sync the Gradle files and download necessary dependencies.
5. **Generate Signed Bundle**:
   - From the top menu bar, click **Build > Generate Signed Bundle / APK...**
   - Select **Android App Bundle** and click Next.
   - For the **Key store path**, click "Choose existing..." and select the `my-upload-key.jks` file located in the root of your project folder.
   - Enter your **Key store password** (`password123`), Key alias (`upload`), and **Key password** (`password123`).
   - Click Next, select the **release** build variant, and click **Finish**.
6. **Locate the AAB**: Once the build completes, Android Studio will show a popup. Click "locate" or manually navigate to `app/release/app-release.aab` inside your project folder.

Upload this `.aab` file to the Google Play Console!
