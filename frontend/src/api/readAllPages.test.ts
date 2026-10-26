import { describe, expect, it, vi } from "vitest";
import { readAllPages } from "./readAllPages";

describe("administration directory pagination", () => {
  it("includes records after the first hundred", async () => {
    const records = Array.from({ length: 105 }, (_, id) => ({ id }));
    const fetchPage = vi.fn(async (page: number, size: number) => ({
      content: records.slice(page * size, (page + 1) * size),
    }));
    expect(await readAllPages(fetchPage)).toEqual(records);
    expect(fetchPage).toHaveBeenCalledTimes(2);
    expect(fetchPage).toHaveBeenLastCalledWith(1, 100);
  });

  it("reports later-page failures instead of showing an incomplete directory", async () => {
    const fetchPage = vi
      .fn()
      .mockResolvedValueOnce({
        content: Array.from({ length: 100 }, (_, id) => ({ id })),
      })
      .mockRejectedValueOnce(new Error("Connexion interrompue"));
    await expect(readAllPages(fetchPage)).rejects.toThrow(
      "Connexion interrompue",
    );
  });
});
