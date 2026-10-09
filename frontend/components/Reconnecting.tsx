/**
 * Across the top while the socket is down (`online === false`; null before the first connect). STOMP reconnects by
 * itself, and the SYNC it gets on reconnect puts the screen back where the Game is. `detail` is a second line for the
 * phone, whose Player needs telling there's nothing to do.
 */
export function Reconnecting({ online, detail }: { online: boolean | null; detail?: string }) {
  if (online !== false) return null;
  return (
    <p role="status" className="fixed inset-x-0 top-0 z-10 bg-danger p-2 text-center text-xl font-bold text-black">
      Reconnecting…
      {detail && <span className="block text-base font-normal">{detail}</span>}
    </p>
  );
}
