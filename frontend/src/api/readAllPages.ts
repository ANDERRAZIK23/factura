// Administration directories filter locally, so include every server page.
export async function readAllPages<T>(
  fetchPage: (page: number, size: number) => Promise<{ content: T[] }>,
) {
  const size = 100;
  const records: T[] = [];
  for (let page = 0; ; page++) {
    const result = await fetchPage(page, size);
    records.push(...result.content);
    if (result.content.length < size) return records;
  }
}
