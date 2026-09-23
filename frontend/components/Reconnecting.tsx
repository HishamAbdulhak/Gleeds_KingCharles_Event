/**
 * Across the top while the socket is down (`online === false`; null before the first connect). STOMP reconnects by
 * itself, and the SYNC it gets on reconnect puts the screen back where the Game is.
 */
export function Reconnecting({ online }: { online: boolean | null }) {
  if (online !== false) return null;
  return (
    <p role="status" className="fixed inset-x-0 top-0 z-10 bg-red-700 p-2 text-center text-xl font-bold text-cream">
      Reconnecting…
    </p>
  );
}
