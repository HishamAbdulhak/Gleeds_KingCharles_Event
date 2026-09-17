export default function Home() {
  return (
    <main className="flex flex-1 flex-col items-center justify-center gap-6 p-8 text-center">
      <p className="text-sm uppercase tracking-[0.3em] text-gold-300">Gleeds presents</p>
      <h1 className="text-5xl font-bold text-gold-500 sm:text-7xl">King Charles Quiz</h1>
      <p className="max-w-md text-lg text-cream/80">
        UK–Saudi relations trivia. Scan the code on the big screen to play.
      </p>
      <div className="h-1 w-24 rounded bg-saudi" />
    </main>
  );
}
