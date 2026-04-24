import { Link } from 'react-router-dom';

export function NotFoundPage() {
  return (
    <div className="text-center py-12">
      <h1 className="text-4xl font-bold text-gray-900">404</h1>
      <p className="mt-2 text-gray-600">Page not found</p>
      <Link to="/" className="mt-4 inline-block text-indigo-600 hover:text-indigo-500">
        Go home
      </Link>
    </div>
  );
}
