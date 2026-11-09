# Prime Mobile Készlet- és Számlanyilvántartó - Frontend

Modern Angular 21 web client for the Prime Mobile inventory and procurement tracking system.

## Features
- **Standalone Components & Zoneless Change Detection**
- **Clean File Separation**: Every component has dedicated `.ts`, `.html`, and `.scss` files.
- **FontAwesome Vector Icons** (No emojis).
- **Responsive & Modern UI**: Built with responsive CSS Grid/Flexbox and Hungarian user interface.

## Tech Stack
- Angular 21 (CLI 21.0.3)
- SCSS Styling
- RxJS 7.8
- FontAwesome 7

## Development Server
To run locally against the Spring Boot API:
```bash
npm start
# App available at http://localhost:4200/
```

## Production Build
To compile the static production bundle for deployment to `inventory.primemobile.hu`:
```bash
npm run build
# Output: dist/frontend/browser
```

The resulting files in `dist/frontend/browser` are served directly via Nginx on the Debian 13 VPS.
