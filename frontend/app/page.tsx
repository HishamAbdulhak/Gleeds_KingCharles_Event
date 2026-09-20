import { redirect } from "next/navigation";

/** No landing page in the spec: the QR code points at /play, so does the bare domain. */
export default function Home() {
  redirect("/play");
}
