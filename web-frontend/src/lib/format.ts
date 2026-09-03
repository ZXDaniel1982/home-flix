export function formatRuntime(ticks: number): string {
	const minutes = Math.round(ticks / 600_000_000);
	const h = Math.floor(minutes / 60);
	const m = minutes % 60;
	if (h === 0) return `${m}m`;
	return `${h}h ${m}m`;
}
