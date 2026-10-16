/**
 * Demo shortcuts (seeded firm buttons, the demo password hint, prefilled sign-in fields) only show when the
 * build sets NEXT_PUBLIC_DEMO_DATA=true. It mirrors LEGALSUITE_DEMO_DATA on the API. Production leaves it unset.
 */
export const DEMO_MODE = process.env.NEXT_PUBLIC_DEMO_DATA === "true";
