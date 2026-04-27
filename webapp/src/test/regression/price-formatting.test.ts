/**
 * REGRESSION TEST — DO NOT MODIFY WITH AI AGENTS.
 *
 * These tests lock down the French locale price formatting contract.
 * Only a human should change expected values after verifying the business logic change is intentional.
 */
describe("Price formatting with fr-FR locale — regression", () => {
  const format = (value: number): string => value.toLocaleString("fr-FR");

  it("formats 1500 as '1 500' with narrow no-break space", () => {
    const result = format(1500);
    expect(result).toMatch(/^1[\s\u202f\u00a0]500$/);
  });

  it("formats 250000 as '250 000'", () => {
    const result = format(250000);
    expect(result).toMatch(/^250[\s\u202f\u00a0]000$/);
  });

  it("formats 1234567 as '1 234 567'", () => {
    const result = format(1234567);
    expect(result).toMatch(/^1[\s\u202f\u00a0]234[\s\u202f\u00a0]567$/);
  });

  it("formats 0 as '0'", () => {
    expect(format(0)).toBe("0");
  });

  it("formats negative number -42000 with minus sign and grouping", () => {
    const result = format(-42000);
    expect(result).toMatch(/^-42[\s\u202f\u00a0]000$/);
  });

  it("formats very large number 9999999999 with correct grouping", () => {
    const result = format(9999999999);
    expect(result).toMatch(/^9[\s\u202f\u00a0]999[\s\u202f\u00a0]999[\s\u202f\u00a0]999$/);
  });

  it("formats decimal 1234.56 with comma as decimal separator", () => {
    const result = format(1234.56);
    expect(result).toMatch(/^1[\s\u202f\u00a0]234,56$/);
  });

  it("formats small decimal 0.99 as '0,99'", () => {
    expect(format(0.99)).toBe("0,99");
  });

  it("formats 100 without any grouping separator", () => {
    expect(format(100)).toBe("100");
  });

  it("formats 999 without any grouping separator", () => {
    expect(format(999)).toBe("999");
  });

  it("formats 1000 with one grouping separator", () => {
    const result = format(1000);
    expect(result).toMatch(/^1[\s\u202f\u00a0]000$/);
  });

  it("formats negative decimal -0.5 as '-0,5'", () => {
    expect(format(-0.5)).toBe("-0,5");
  });
});
