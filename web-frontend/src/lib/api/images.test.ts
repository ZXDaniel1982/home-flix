import { describe, expect, it } from 'vitest';
import { imageUrl } from './images';

describe('imageUrl', () => {
	it('builds a primary image URL', () => {
		expect(imageUrl('m1', 'tag1')).toBe('/api/Items/m1/Images/Primary?tag=tag1');
	});

	it('builds a backdrop image URL and encodes the tag', () => {
		expect(imageUrl('m1', 'a b/c', 'Backdrop')).toBe('/api/Items/m1/Images/Backdrop?tag=a%20b%2Fc');
	});
});
