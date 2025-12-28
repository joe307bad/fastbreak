import type { Metadata } from 'next';
import Link from 'next/link';

export const metadata: Metadata = {
  title: 'Privacy Policy - fastbreak',
  description: 'Privacy policy for the fastbreak sports analytics dashboard',
};

export default function PrivacyPolicy() {
  return (
    <main className="max-w-4xl mx-auto px-4 md:px-8 py-8">
      <article className="space-y-8">
        <Link
          href="/"
          className="inline-block mb-6 text-sm text-[var(--muted)] hover:text-[var(--foreground)] transition-colors"
        >
          ← Back to Home
        </Link>

        <header>
          <h1 className="text-3xl md:text-4xl font-bold mb-2">Privacy Policy</h1>
          <p className="text-sm text-[var(--muted)]">
            Last updated: December 28, 2025
          </p>
        </header>

        <section className="space-y-4">
          <h2 className="text-2xl font-bold">Introduction</h2>
          <p>
            Welcome to fastbreak. This privacy policy explains how we handle information when you use our sports analytics dashboard, including the website at fastbreak.joebad.com and our native iOS and Android mobile applications (collectively, the "Service").
          </p>
          <p>
            We are committed to protecting your privacy. This policy outlines what information we collect, how we use it, and your rights regarding that information.
          </p>
        </section>

        <section className="space-y-4">
          <h2 className="text-2xl font-bold">Information We Collect</h2>

          <div className="space-y-3">
            <h3 className="text-xl font-semibold">Information You Provide</h3>
            <p>
              We do not require you to create an account or provide personal information to use the Service. The Service is accessible without registration.
            </p>
          </div>

          <div className="space-y-3">
            <h3 className="text-xl font-semibold">Automatically Collected Information</h3>
            <p>
              When you access the Service, we may automatically collect certain technical information, including:
            </p>
            <ul className="list-disc pl-6 space-y-1">
              <li>Browser type and version (web)</li>
              <li>Operating system and device type</li>
              <li>App version (mobile apps)</li>
              <li>Pages visited and features used</li>
              <li>Time and date of access</li>
              <li>IP address (anonymized)</li>
              <li>Referring website (web only)</li>
            </ul>
          </div>

          <div className="space-y-3">
            <h3 className="text-xl font-semibold">Local Storage</h3>
            <p>
              We use local storage (browser local storage on web, device storage on mobile apps) to save your preferences, such as:
            </p>
            <ul className="list-disc pl-6 space-y-1">
              <li>Theme preference (light/dark mode)</li>
              <li>Sport selection preference</li>
            </ul>
            <p>
              This information is stored locally on your device and is not transmitted to our servers.
            </p>
          </div>

          <div className="space-y-3">
            <h3 className="text-xl font-semibold">Mobile App Permissions</h3>
            <p>
              Our mobile applications do not require any special device permissions beyond basic internet access to retrieve sports data. We do not access your camera, microphone, location, contacts, or other sensitive device features.
            </p>
          </div>
        </section>

        <section className="space-y-4">
          <h2 className="text-2xl font-bold">How We Use Your Information</h2>
          <p>
            We use the collected information for the following purposes:
          </p>
          <ul className="list-disc pl-6 space-y-1">
            <li>To provide and maintain the Service</li>
            <li>To improve and optimize the Service</li>
            <li>To understand how users interact with the Service</li>
            <li>To detect and prevent technical issues</li>
            <li>To analyze usage patterns and trends</li>
          </ul>
        </section>

        <section className="space-y-4">
          <h2 className="text-2xl font-bold">Data Sources</h2>
          <p>
            The sports data displayed on the Service is sourced from publicly available data providers, including:
          </p>
          <ul className="list-disc pl-6 space-y-1">
            <li>nflfastR / nflreadr (NFL data)</li>
            <li>NBA API (NBA data)</li>
            <li>NHL API (NHL data)</li>
            <li>MLB data providers (MLB data)</li>
            <li>PlayoffStatus.com (playoff probability data)</li>
          </ul>
          <p>
            All sports statistics and data are publicly available and not personally identifiable.
          </p>
        </section>

        <section className="space-y-4">
          <h2 className="text-2xl font-bold">Cookies and Tracking Technologies</h2>
          <p>
            We may use cookies and similar tracking technologies to track activity on the Service and store certain information. You can instruct your browser to refuse all cookies or to indicate when a cookie is being sent. However, if you do not accept cookies, you may not be able to use some portions of the Service.
          </p>
        </section>

        <section className="space-y-4">
          <h2 className="text-2xl font-bold">Third-Party Services</h2>
          <p>
            We may use third-party services to help us operate and improve the Service. These third parties may have access to your information only to perform specific tasks on our behalf and are obligated not to disclose or use it for any other purpose.
          </p>
          <p>
            The Service is hosted on infrastructure that may collect anonymous usage statistics for operational purposes.
          </p>
        </section>

        <section className="space-y-4">
          <h2 className="text-2xl font-bold">Data Security</h2>
          <p>
            We value your trust in providing us your information and strive to use commercially acceptable means of protecting it. However, no method of transmission over the internet or electronic storage is 100% secure, and we cannot guarantee its absolute security.
          </p>
        </section>

        <section className="space-y-4">
          <h2 className="text-2xl font-bold">Children's Privacy</h2>
          <p>
            The Service is not directed to individuals under the age of 13. We do not knowingly collect personally identifiable information from children under 13. If you are a parent or guardian and you are aware that your child has provided us with personal information, please contact us so that we can take necessary action.
          </p>
        </section>

        <section className="space-y-4">
          <h2 className="text-2xl font-bold">Changes to This Privacy Policy</h2>
          <p>
            We may update our privacy policy from time to time. We will notify you of any changes by posting the new privacy policy on this page and updating the "Last updated" date at the top of this policy.
          </p>
          <p>
            You are advised to review this privacy policy periodically for any changes. Changes to this privacy policy are effective when they are posted on this page.
          </p>
        </section>

        <section className="space-y-4">
          <h2 className="text-2xl font-bold">Your Rights</h2>
          <p>
            Depending on your location, you may have certain rights regarding your personal information, including:
          </p>
          <ul className="list-disc pl-6 space-y-1">
            <li>The right to access the personal information we hold about you</li>
            <li>The right to request correction of inaccurate information</li>
            <li>The right to request deletion of your information</li>
            <li>The right to object to processing of your information</li>
            <li>The right to data portability</li>
          </ul>
        </section>

        <section className="space-y-4">
          <h2 className="text-2xl font-bold">Contact Us</h2>
          <p>
            If you have any questions about this privacy policy or our practices, please contact us at:
          </p>
          <p>
            Email: joe307bad@gmail.com
          </p>
        </section>

        <section className="space-y-4">
          <h2 className="text-2xl font-bold">Platform-Specific Information</h2>

          <div className="space-y-3">
            <h3 className="text-xl font-semibold">iOS App (Beta)</h3>
            <p>
              Our iOS application is currently in beta and not yet available on the Apple App Store. The app does not collect or transmit any personal information beyond what is described in this privacy policy. We do not use Apple's advertising identifier (IDFA) or track users across apps and websites.
            </p>
          </div>

          <div className="space-y-3">
            <h3 className="text-xl font-semibold">Android App (Beta)</h3>
            <p>
              Our Android application is currently in beta and not yet available on Google Play. The app does not collect or transmit any personal information beyond what is described in this privacy policy. We do not use Google's advertising identifier or track users across apps and websites.
            </p>
          </div>
        </section>

        <section className="space-y-4">
          <h2 className="text-2xl font-bold">Disclaimer</h2>
          <p>
            The Service provides sports statistics and analytics for informational and entertainment purposes only. We do not guarantee the accuracy, completeness, or timeliness of any data displayed on the Service.
          </p>
        </section>
      </article>
    </main>
  );
}
